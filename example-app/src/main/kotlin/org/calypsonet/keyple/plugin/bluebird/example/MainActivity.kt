/* **************************************************************************************
 * Copyright (c) 2025 Calypso Networks Association https://calypsonet.org/
 *
 * See the NOTICE file(s) distributed with this work for additional information
 * regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License 2.0 which is available at http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ************************************************************************************** */
@file:Suppress("UNNECESSARY_SAFE_CALL")

package org.calypsonet.keyple.plugin.bluebird.example

import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.annotation.StringRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.calypsonet.keypl.BluebirdConstants
import org.calypsonet.keyple.plugin.bluebird.*
import org.calypsonet.keyple.plugin.bluebird.example.MessageDisplayAdapter.Message
import org.calypsonet.keyple.plugin.bluebird.example.MessageDisplayAdapter.MessageType
import org.calypsonet.keyple.plugin.bluebird.example.databinding.ActivityMainBinding
import org.eclipse.keyple.card.calypso.CalypsoExtensionService
import org.eclipse.keyple.card.calypso.crypto.legacysam.LegacySamExtensionService
import org.eclipse.keyple.card.calypso.crypto.legacysam.LegacySamUtil
import org.eclipse.keyple.core.service.*
import org.eclipse.keyple.core.util.HexUtil
import org.eclipse.keypop.calypso.card.WriteAccessLevel
import org.eclipse.keypop.calypso.card.card.CalypsoCard
import org.eclipse.keypop.calypso.card.transaction.ChannelControl.CLOSE_AFTER
import org.eclipse.keypop.calypso.card.transaction.ChannelControl.KEEP_OPEN
import org.eclipse.keypop.calypso.card.transaction.SecureRegularModeTransactionManager
import org.eclipse.keypop.calypso.card.transaction.SymmetricCryptoSecuritySetting
import org.eclipse.keypop.calypso.crypto.legacysam.sam.LegacySam
import org.eclipse.keypop.reader.*
import org.eclipse.keypop.reader.ObservableCardReader.DetectionMode.REPEATING
import org.eclipse.keypop.reader.ObservableCardReader.NotificationMode.ALWAYS
import org.eclipse.keypop.reader.selection.CardSelectionManager
import org.eclipse.keypop.reader.spi.CardReaderObservationExceptionHandlerSpi
import org.eclipse.keypop.reader.spi.CardReaderObserverSpi
import timber.log.Timber

class MainActivity :
    AppCompatActivity(), CardReaderObserverSpi, CardReaderObservationExceptionHandlerSpi {

  private val smartCardService = SmartCardServiceProvider.getService()
  private val readerApiFactory: ReaderApiFactory =
      SmartCardServiceProvider.getService().readerApiFactory
  private val calypsoExtensionService = CalypsoExtensionService.getInstance()
  private val cardSelectionManager = smartCardService.readerApiFactory.createCardSelectionManager()

  private lateinit var cardReader: ObservableCardReader
  private lateinit var samReader: CardReader
  private var isInitialized = false

  private lateinit var binding: ActivityMainBinding
  private lateinit var messageDisplayAdapter: RecyclerView.Adapter<*>
  private lateinit var layoutManager: RecyclerView.LayoutManager
  private val messages = arrayListOf<Message>()

  private lateinit var securitySettings: SymmetricCryptoSecuritySetting

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    initViews()
    initRecyclerView()
    if (ContextCompat.checkSelfPermission(
        this, BluebirdConstants.BLUEBIRD_SAM_ANDROID_PERMISSION) ==
        PackageManager.PERMISSION_GRANTED) {
      Timber.i("SAM access permission already granted")
      finalizeInitialization()
    } else {
      Timber.i("Request SAM access permission")
      checkSamAccessPermission()
    }
  }

  override fun onResume() {
    super.onResume()
    if (isInitialized) {
      cardReader.startCardDetection(REPEATING)
      addMessage(
          MessageType.ACTION, "Waiting for card presentation...\nAID=${CalypsoConstants.AID}")
    }
  }

  override fun onPause() {
    if (isInitialized) {
      cardReader.stopCardDetection()
      addMessage(MessageType.ACTION, "Card detection stopped")
    }
    super.onPause()
  }

  override fun onDestroy() {
    if (isInitialized) {
      cardReader.let { cardReader.removeObserver(this) }
      smartCardService?.plugins?.forEach { smartCardService?.unregisterPlugin(it.name) }
    }
    super.onDestroy()
  }

  private fun initViews() {
    binding = ActivityMainBinding.inflate(layoutInflater)
    setContentView(binding.root)
    setSupportActionBar(binding.toolbar)
    val actionBar = supportActionBar
    actionBar?.title = "Keyple demo"
    actionBar?.subtitle = "Bluebird Plugin"
  }

  private fun initRecyclerView() {
    messageDisplayAdapter = MessageDisplayAdapter(messages)
    layoutManager = LinearLayoutManager(this)
    binding.messageRecyclerView.layoutManager = layoutManager
    binding.messageRecyclerView.adapter = messageDisplayAdapter
    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
  }

  private fun showAlertDialogWithAction(
      @StringRes titleRes: Int,
      @StringRes messageRes: Int,
      onOkClick: () -> Unit
  ) {
    AlertDialog.Builder(this)
        .setTitle(getString(titleRes))
        .setMessage(getString(messageRes))
        .setPositiveButton("OK") { _, _ -> onOkClick() }
        .setCancelable(false)
        .show()
  }

  private fun addMessage(type: MessageType, message: String) {
    CoroutineScope(Dispatchers.Main).launch {
      messages.add(Message(type, message))
      messageDisplayAdapter.notifyItemInserted(messages.lastIndex)
      binding.messageRecyclerView.smoothScrollToPosition(messages.size - 1)
    }
    Timber.d("${type.name}: %s", message)
  }

  private fun checkSamAccessPermission() {
    Timber.d("Checking SAM access permission")
    val requestPermissionLauncher =
        registerForActivityResult(RequestPermission()) { isGranted ->
          if (isGranted) {
            Timber.i("Permission granted")
            finalizeInitialization()
          } else {
            Timber.i("Permission denied")
            showAlertDialogWithAction(
                R.string.permission_denied_title,
                R.string.permission_denied_message,
                onOkClick = { finishAffinity() })
          }
        }
    Timber.d("Showing permission request dialog")
    showAlertDialogWithAction(
        R.string.permission_request_title,
        R.string.permission_request_message,
        onOkClick = {
          requestPermissionLauncher.launch(BluebirdConstants.BLUEBIRD_SAM_ANDROID_PERMISSION)
        })
  }

  private fun finalizeInitialization() {

    try {
      initReaders()
      initSecuritySettings()
      prepareCardSelection()
    } catch (_: Exception) {
      showAlertDialogWithAction(R.string.init_error_title, R.string.init_error_message) {
        finishAffinity()
      }
    }
    isInitialized = true
  }

  private fun initReaders() {
    Timber.i("Initializing readers...")
    val pluginFactory = BluebirdPluginFactoryProvider.provideFactory(this)

    val bluebirdPlugin = smartCardService.registerPlugin(pluginFactory)
    cardReader =
        bluebirdPlugin.getReader(BluebirdConstants.CARD_READER_NAME) as ObservableCardReader

    cardReader.setReaderObservationExceptionHandler(this)
    cardReader.addObserver(this)

    bluebirdPlugin
        .getReaderExtension(BluebirdCardReader::class.java, BluebirdConstants.CARD_READER_NAME)
        .setSkyEcpVasupPayload(HexUtil.toByteArray(CalypsoConstants.VASUP_PAYLOAD))

    with(cardReader as ConfigurableCardReader) {
      activateProtocol(
          BluebirdContactlessProtocols.INNOVATRON_B_PRIME.name, "INNOVATRON_B_PRIME_CARD")
      activateProtocol(BluebirdContactlessProtocols.ISO_14443_4_B_SKY_ECP.name, "ISO_14443_4_CARD")
    }

    samReader = bluebirdPlugin.getReader(BluebirdConstants.SAM_READER_NAME)
    Timber.i("Readers initialized")
  }

  private fun initSecuritySettings() {
    Timber.i("Initializing security settings...")
    val samSelectionManager: CardSelectionManager =
        smartCardService.readerApiFactory.createCardSelectionManager()

    samSelectionManager.prepareSelection(
        readerApiFactory
            .createBasicCardSelector()
            .filterByPowerOnData(
                LegacySamUtil.buildPowerOnDataFilter(LegacySam.ProductType.SAM_C1, null)),
        LegacySamExtensionService.getInstance()
            .legacySamApiFactory
            .createLegacySamSelectionExtension())

    try {
      val samSelectionResult = samSelectionManager.processCardSelectionScenario(samReader)

      val legacySam = samSelectionResult.activeSmartCard!! as LegacySam
      val cryptoCardTransactionManagerFactory =
          LegacySamExtensionService.getInstance()
              .legacySamApiFactory
              .createSymmetricCryptoCardTransactionManagerFactory(samReader, legacySam)

      securitySettings =
          calypsoExtensionService.calypsoCardApiFactory.createSymmetricCryptoSecuritySetting(
              cryptoCardTransactionManagerFactory)
    } catch (e: Exception) {
      Timber.e(e, "An exception occurred while selecting the SAM. ${e.message}")
      showAlertDialogWithAction(
          R.string.sam_error_title, R.string.sam_error_message, onOkClick = { finishAffinity() })
    }
    Timber.i("Security settings initialized")
  }

  private fun prepareCardSelection() {
    Timber.i("Preparing card selection...")
    cardSelectionManager.prepareSelection(
        smartCardService.readerApiFactory
            .createIsoCardSelector()
            .filterByDfName(CalypsoConstants.AID),
        calypsoExtensionService.calypsoCardApiFactory.createCalypsoCardSelectionExtension())
    cardSelectionManager.scheduleCardSelectionScenario(cardReader, ALWAYS)
    Timber.i("Card selection prepared")
  }

  override fun onReaderEvent(readerEvent: CardReaderEvent) {
    CoroutineScope(Dispatchers.IO).launch {
      when (readerEvent.type) {
        CardReaderEvent.Type.CARD_MATCHED -> handleCardMatchedEvent(readerEvent)
        CardReaderEvent.Type.CARD_INSERTED -> handleCardInsertedEvent()
        CardReaderEvent.Type.CARD_REMOVED -> handleCardRemovedEvent()
        else -> {
          // Do nothing
        }
      }
    }
  }

  override fun onReaderObservationError(pluginName: String, readerName: String, e: Throwable) {
    addMessage(MessageType.EVENT, "Reader observation error: ${e.message}")
    Timber.e(e, "Reader observation error: ${e.message}")
  }

  private fun handleCardMatchedEvent(cardReaderEvent: CardReaderEvent) {
    try {
      addMessage(MessageType.ACTION, "Process selection")
      val selectionsResult =
          cardSelectionManager.parseScheduledCardSelectionsResponse(
              cardReaderEvent.scheduledCardSelectionsResponse)

      addMessage(MessageType.RESULT, "Calypso card selection: SUCCESS")
      val calypsoCard = selectionsResult.activeSmartCard as CalypsoCard
      addMessage(MessageType.RESULT, "DFNAME: ${HexUtil.toHex(calypsoCard.dfName)}")

      val efEnvironmentHolder =
          HexUtil.toHex(
              calypsoCard.getFileBySfi(CalypsoConstants.SFI_EnvironmentAndHolder).data.content)

      addMessage(MessageType.RESULT, "Environment and Holder file: $efEnvironmentHolder")

      val cardTransactionManager =
          calypsoExtensionService.calypsoCardApiFactory.createSecureRegularModeTransactionManager(
              cardReader, calypsoCard, securitySettings)

      addMessage(MessageType.ACTION, "Read EventLog and counter in secure session")
      (cardTransactionManager as SecureRegularModeTransactionManager)
          .prepareOpenSecureSession(WriteAccessLevel.LOAD)
          .prepareReadRecords(
              CalypsoConstants.SFI_EventLog,
              CalypsoConstants.RECORD_NUMBER_1,
              CalypsoConstants.RECORD_NUMBER_1,
              CalypsoConstants.RECORD_SIZE)
          .prepareReadRecords(
              CalypsoConstants.SFI_Counter1,
              CalypsoConstants.RECORD_NUMBER_1,
              CalypsoConstants.RECORD_NUMBER_1,
              CalypsoConstants.RECORD_SIZE)
          .processCommands(KEEP_OPEN)
      val eventLog =
          HexUtil.toHex(calypsoCard.getFileBySfi(CalypsoConstants.SFI_EventLog).data.content)
      val counter =
          calypsoCard
              .getFileBySfi(CalypsoConstants.SFI_Counter1)
              .data
              .getContentAsCounterValue(CalypsoConstants.RECORD_NUMBER_1)
      addMessage(MessageType.RESULT, "EventLog file: $eventLog")
      addMessage(MessageType.RESULT, "Counter value: $counter")
      addMessage(MessageType.ACTION, "Increment counter by 1 and close session")
      cardTransactionManager
          .prepareIncreaseCounter(CalypsoConstants.SFI_Counter1, 1, 1)
          .prepareCloseSecureSession()
          .processCommands(CLOSE_AFTER)
      addMessage(MessageType.RESULT, "Closing session: SUCCESS")

      addMessage(MessageType.RESULT, "End of the Calypso card processing.")
      addMessage(MessageType.RESULT, "You can remove the card now")
    } catch (e: Exception) {
      Timber.e(e)
      addMessage(MessageType.RESULT, "Exception: ${e.message}")
    } finally {
      cardReader.finalizeCardProcessing()
    }
  }

  private fun handleCardInsertedEvent() {
    addMessage(
        MessageType.EVENT,
        "Unrecognized card: ${(cardReader as ConfigurableCardReader).currentProtocol}")
    addMessage(MessageType.ACTION, "Waiting for card removal...")
    cardReader.finalizeCardProcessing()
  }

  private fun handleCardRemovedEvent() {
    addMessage(MessageType.EVENT, "Card removed")
    addMessage(MessageType.ACTION, "Waiting for card presentation...\nAID=${CalypsoConstants.AID}")
  }
}
