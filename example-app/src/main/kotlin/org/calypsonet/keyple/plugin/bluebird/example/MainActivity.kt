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

  private val cardSelectionManager =
      SmartCardServiceProvider.getService().readerApiFactory.createCardSelectionManager()
  private lateinit var securitySettings: SymmetricCryptoSecuritySetting

  private lateinit var cardReader: ObservableCardReader
  private lateinit var samReader: CardReader
  private var isInitializationFinalized = false

  private lateinit var binding: ActivityMainBinding
  private lateinit var messageDisplayAdapter: RecyclerView.Adapter<*>
  private val messages = arrayListOf<Message>()

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    initUI()
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
    if (isInitializationFinalized) {
      cardReader.startCardDetection(REPEATING)
      addMessage(
          MessageType.ACTION, "Waiting for card presentation...\nAID: ${CalypsoConstants.AID}")
    }
  }

  override fun onPause() {
    if (isInitializationFinalized) {
      cardReader.stopCardDetection()
      addMessage(MessageType.ACTION, "Card detection stopped")
    }
    super.onPause()
  }

  override fun onDestroy() {
    if (isInitializationFinalized) {
      cardReader.let { cardReader.removeObserver(this) }
      SmartCardServiceProvider.getService()?.plugins?.forEach {
        SmartCardServiceProvider.getService()?.unregisterPlugin(it.name)
      }
    }
    super.onDestroy()
  }

  private fun initUI() {
    binding = ActivityMainBinding.inflate(layoutInflater)
    setContentView(binding.root)

    setSupportActionBar(binding.toolbar)
    supportActionBar?.title = getString(R.string.app_name)
    supportActionBar?.subtitle = "Bluebird Plugin"

    messageDisplayAdapter = MessageDisplayAdapter(messages)
    binding.messageRecyclerView.layoutManager = LinearLayoutManager(this)
    binding.messageRecyclerView.adapter = messageDisplayAdapter

    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
  }

  private fun addMessage(type: MessageType, message: String) {
    CoroutineScope(Dispatchers.Main).launch {
      messages.add(Message(type, message))
      messageDisplayAdapter.notifyItemInserted(messages.lastIndex)
      binding.messageRecyclerView.smoothScrollToPosition(messages.size - 1)
    }
    Timber.d("${type.name}: %s", message)
  }

  private fun showAlertDialogWithAction(
      titleRes: String,
      messageRes: String,
      onOkClick: () -> Unit
  ) {
    AlertDialog.Builder(this)
        .setTitle(titleRes)
        .setMessage(messageRes)
        .setPositiveButton("OK") { _, _ -> onOkClick() }
        .setCancelable(false)
        .show()
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
                "SAM Access Permission Denied",
                "You must accept the requested permissions to continue. Please relaunch the app.",
                onOkClick = { finishAffinity() })
          }
        }
    Timber.d("Showing permission request dialog")
    showAlertDialogWithAction(
        "SAM Access Permission Required",
        "Please grant access to the SAM. This permission request appears only on the first use, and the application must be restarted after granting it.",
        onOkClick = {
          requestPermissionLauncher.launch(BluebirdConstants.BLUEBIRD_SAM_ANDROID_PERMISSION)
        })
  }

  private fun finalizeInitialization() {
    try {
      initReaders()
      initSecuritySettings()
      prepareCardSelection()
    } catch (e: Exception) {
      Timber.e(e, "Initialization Error: ${e.message}")
      showAlertDialogWithAction("Initialization Error", "Unable to initialize the application") {
        finishAffinity()
      }
    }
    isInitializationFinalized = true
  }

  private fun initReaders() {
    Timber.i("Initializing readers...")

    // register plugin
    val bluebirdPlugin =
        SmartCardServiceProvider.getService()
            .registerPlugin(BluebirdPluginFactoryProvider.provideFactory(this))

    // init card reader
    cardReader =
        bluebirdPlugin.getReader(BluebirdConstants.CARD_READER_NAME) as ObservableCardReader

    cardReader.setReaderObservationExceptionHandler(this)
    cardReader.addObserver(this)

    // configure ECP
    bluebirdPlugin
        .getReaderExtension(BluebirdCardReader::class.java, BluebirdConstants.CARD_READER_NAME)
        .setSkyEcpVasupPayload(HexUtil.toByteArray(CalypsoConstants.VASUP_PAYLOAD))

    with(cardReader as ConfigurableCardReader) {
      activateProtocol(
          BluebirdContactlessProtocols.INNOVATRON_B_PRIME.name, "INNOVATRON_B_PRIME_CARD")
      activateProtocol(BluebirdContactlessProtocols.ISO_14443_4_B_SKY_ECP.name, "ISO_14443_4_CARD")
    }

    // init SAM reader
    samReader = bluebirdPlugin.getReader(BluebirdConstants.SAM_READER_NAME)
    Timber.i("Readers initialized")
  }

  private fun initSecuritySettings() {
    Timber.i("Initializing security settings...")
    val samSelectionManager: CardSelectionManager =
        SmartCardServiceProvider.getService().readerApiFactory.createCardSelectionManager()

    samSelectionManager.prepareSelection(
        SmartCardServiceProvider.getService()
            .readerApiFactory
            .createBasicCardSelector()
            .filterByPowerOnData(
                LegacySamUtil.buildPowerOnDataFilter(LegacySam.ProductType.SAM_C1, null)),
        LegacySamExtensionService.getInstance()
            .legacySamApiFactory
            .createLegacySamSelectionExtension())

    try {
      val samSelectionResult = samSelectionManager.processCardSelectionScenario(samReader)

      securitySettings =
          CalypsoExtensionService.getInstance()
              .calypsoCardApiFactory
              .createSymmetricCryptoSecuritySetting(
                  LegacySamExtensionService.getInstance()
                      .legacySamApiFactory
                      .createSymmetricCryptoCardTransactionManagerFactory(
                          samReader, samSelectionResult.activeSmartCard!! as LegacySam))
    } catch (e: Exception) {
      Timber.e(e, "An exception occurred while selecting the SAM. ${e.message}")
      showAlertDialogWithAction(
          "SAM Error", "Unable to communicate with the SAM", onOkClick = { finishAffinity() })
    }
    Timber.i("Security settings initialized")
  }

  private fun prepareCardSelection() {
    Timber.i("Preparing card selection...")
    cardSelectionManager.prepareSelection(
        SmartCardServiceProvider.getService()
            .readerApiFactory
            .createIsoCardSelector()
            .filterByDfName(CalypsoConstants.AID),
        CalypsoExtensionService.getInstance()
            .calypsoCardApiFactory
            .createCalypsoCardSelectionExtension())
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
      addMessage(MessageType.EVENT, "Card matched")
      val selectionsResult =
          cardSelectionManager.parseScheduledCardSelectionsResponse(
              cardReaderEvent.scheduledCardSelectionsResponse)

      val calypsoCard = selectionsResult.activeSmartCard as CalypsoCard
      addMessage(MessageType.RESULT, "DFNAME:\n${HexUtil.toHex(calypsoCard.dfName)}")

      val cardTransactionManager =
          CalypsoExtensionService.getInstance()
              .calypsoCardApiFactory
              .createSecureRegularModeTransactionManager(cardReader, calypsoCard, securitySettings)

      addMessage(MessageType.ACTION, "Starting secure transaction...")

      val d1 = System.currentTimeMillis()

      (cardTransactionManager as SecureRegularModeTransactionManager)
          .prepareOpenSecureSession(WriteAccessLevel.LOAD)
          .prepareReadRecords(
              CalypsoConstants.SFI_EnvironmentAndHolder,
              CalypsoConstants.RECORD_NUMBER_1,
              CalypsoConstants.RECORD_NUMBER_1,
              CalypsoConstants.RECORD_SIZE)
          .prepareReadRecords(
              CalypsoConstants.SFI_EventLog,
              CalypsoConstants.RECORD_NUMBER_1,
              CalypsoConstants.RECORD_NUMBER_1,
              CalypsoConstants.RECORD_SIZE)
          .prepareCloseSecureSession()
          .processCommands(CLOSE_AFTER)

      val d2 = System.currentTimeMillis()

      val efEnvironmentHolder =
          HexUtil.toHex(
              calypsoCard.getFileBySfi(CalypsoConstants.SFI_EnvironmentAndHolder).data.content)

      val eventLog =
          HexUtil.toHex(calypsoCard.getFileBySfi(CalypsoConstants.SFI_EventLog).data.content)

      addMessage(
          MessageType.RESULT,
          "EnvironmentHolder file:\n$efEnvironmentHolder\n\nEventLog file:\n$eventLog")

      addMessage(MessageType.ACTION, "Transaction duration: ${d2-d1} ms")
    } catch (e: Exception) {
      Timber.e(e)
      addMessage(MessageType.RESULT, "Exception: ${e.message}")
    } finally {
      cardReader.finalizeCardProcessing()
      addMessage(MessageType.ACTION, "Waiting for card removal...")
    }
  }

  private fun handleCardInsertedEvent() {
    addMessage(
        MessageType.EVENT,
        "Unrecognized card: ${(cardReader as ConfigurableCardReader).currentProtocol}")
    cardReader.finalizeCardProcessing()
    addMessage(MessageType.ACTION, "Waiting for card removal...")
  }

  private fun handleCardRemovedEvent() {
    addMessage(MessageType.EVENT, "Card removed")
    addMessage(MessageType.ACTION, "Waiting for card presentation...\nAID: ${CalypsoConstants.AID}")
  }
}
