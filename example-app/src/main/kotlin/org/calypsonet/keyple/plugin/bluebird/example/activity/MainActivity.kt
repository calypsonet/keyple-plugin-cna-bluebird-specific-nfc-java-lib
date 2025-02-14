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

package org.calypsonet.keyple.plugin.bluebird.example.activity

import android.Manifest
import android.app.ProgressDialog
import android.content.pm.PackageManager
import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import org.calypsonet.keyple.plugin.bluebird.*
import org.calypsonet.keyple.plugin.bluebird.example.R
import org.calypsonet.keyple.plugin.bluebird.example.adapter.EventAdapter
import org.calypsonet.keyple.plugin.bluebird.example.databinding.ActivityMainBinding
import org.calypsonet.keyple.plugin.bluebird.example.dialog.PermissionDeniedDialog
import org.calypsonet.keyple.plugin.bluebird.example.model.EventModel
import org.calypsonet.keyple.plugin.bluebird.example.util.CalypsoClassicInfo
import org.calypsonet.keyple.plugin.bluebird.example.util.PermissionHelper
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
import org.eclipse.keypop.calypso.crypto.legacysam.sam.LegacySam
import org.eclipse.keypop.reader.*
import org.eclipse.keypop.reader.ObservableCardReader.DetectionMode.REPEATING
import org.eclipse.keypop.reader.ObservableCardReader.NotificationMode.ALWAYS
import org.eclipse.keypop.reader.selection.CardSelectionManager
import org.eclipse.keypop.reader.selection.ScheduledCardSelectionsResponse
import org.eclipse.keypop.reader.spi.CardReaderObservationExceptionHandlerSpi
import org.eclipse.keypop.reader.spi.CardReaderObserverSpi
import timber.log.Timber

class MainActivity :
    AppCompatActivity(), CardReaderObserverSpi, CardReaderObservationExceptionHandlerSpi {

  private lateinit var cardReader: ConfigurableCardReader
  private lateinit var samReader: CardReader
  private lateinit var legacySam: LegacySam
  private lateinit var binding: ActivityMainBinding
  private var smartCardService: SmartCardService = SmartCardServiceProvider.getService()
  private lateinit var cardSelectionManager: CardSelectionManager
  private val areReadersInitialized = AtomicBoolean(false)
  private lateinit var progress: ProgressDialog

  // Variables for event window
  private lateinit var adapter: RecyclerView.Adapter<*>
  private lateinit var layoutManager: RecyclerView.LayoutManager
  private val events = arrayListOf<EventModel>()

  private val readerApiFactory: ReaderApiFactory =
      SmartCardServiceProvider.getService().readerApiFactory
  private val calypsoExtensionService: CalypsoExtensionService =
      CalypsoExtensionService.getInstance()
  private val calypsoCardApiFactory = calypsoExtensionService.calypsoCardApiFactory
  private lateinit var calypsoCardExtensionProvider: CalypsoExtensionService

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding = ActivityMainBinding.inflate(layoutInflater)
    setContentView(binding.root)

    initContentView()

    // Init recycler view
    adapter = EventAdapter(events)
    layoutManager = LinearLayoutManager(this)
    binding.eventRecyclerView.layoutManager = layoutManager
    binding.eventRecyclerView.adapter = adapter

    progress = ProgressDialog(this)
    progress.setMessage(getString(R.string.please_wait))
    progress.setCancelable(false)

    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
  }

  private fun initContentView() {
    initActionBar(binding.toolbar, "Keyple demo", "Bluebird Plugin")
  }

  private fun initActionBar(toolbar: Toolbar, title: String, subtitle: String) {
    setSupportActionBar(toolbar)
    val actionBar = supportActionBar
    actionBar?.title = title
    actionBar?.subtitle = subtitle
  }

  override fun onResume() {
    super.onResume()

    if (!areReadersInitialized.get()) {
      addActionEvent("Enabling NFC Reader mode")
      addResultEvent("Initializing readers...")
      try {
        initReaders()
        configureCalypsoTransaction()
      } catch (e: Exception) {
        showAlertDialog(e, finish = true, cancelable = false)
      }
    }
    addActionEvent("Start card Read Write Mode")
    (cardReader as ObservableCardReader).startCardDetection(REPEATING)
  }

  private fun initReaders() {
    Timber.d("initReaders")

    val pluginFactory = BluebirdPluginFactoryProvider.getFactory(this)

    smartCardService = SmartCardServiceProvider.getService()
    val bluebirdPlugin = smartCardService!!.registerPlugin(pluginFactory)
    cardReader =
        bluebirdPlugin.getReader(BluebirdContactlessReader.READER_NAME) as ConfigurableCardReader

    (cardReader as ObservableCardReader).setReaderObservationExceptionHandler(this)
    (cardReader as ObservableCardReader).addObserver(this)

    bluebirdPlugin
        .getReaderExtension(
            BluebirdContactlessReader::class.java, BluebirdContactlessReader.READER_NAME)
        .setSkyEcpVasupPayload(HexUtil.toByteArray(CalypsoClassicInfo.VASUP_PAYLOAD))

    cardReader.activateProtocol(
        BluebirdSupportContactlessProtocols.INNOVATRON_B_PRIME.name, "INNOVATRON_B_PRIME_CARD")
    cardReader.activateProtocol(
        BluebirdSupportContactlessProtocols.ISO_14443_4_B_SKY_ECP.name, "ISO_14443_4_CARD")

    samReader = bluebirdPlugin.getReader(BluebirdContactReader.READER_NAME)

    PermissionHelper.checkPermission(
        this@MainActivity,
        arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE, BluebirdPlugin.BLUEBIRD_SAM_PERMISSION))

    val tempSam = getSam(samReader)
    if (tempSam == null) {
      showAlertDialog(Exception("Failed to initialize SAM"), finish = true, cancelable = false)
      return
    }
    legacySam = tempSam
    areReadersInitialized.set(true)
  }

  private fun getSam(samReader: CardReader): LegacySam? {
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
      return samSelectionResult.activeSmartCard!! as LegacySam
    } catch (e: Exception) {
      Timber.e(e)
      Timber.e("An exception occurred while selecting the SAM. ${e.message}")
    }
    return null
  }

  override fun onPause() {
    if (areReadersInitialized.get()) {
      addActionEvent("Stopping card Read Write Mode")
      (cardReader as ObservableCardReader).stopCardDetection()
    }
    super.onPause()
  }

  override fun onDestroy() {
    cardReader.let { (cardReader as ObservableCardReader).removeObserver(this) }
    smartCardService?.plugins?.forEach { smartCardService?.unregisterPlugin(it.name) }
    super.onDestroy()
  }

  // UI Event Management Methods
  protected fun showAlertDialog(t: Throwable, finish: Boolean = false, cancelable: Boolean = true) {
    val builder = AlertDialog.Builder(this@MainActivity)
    builder.setTitle(R.string.alert_dialog_title)
    builder.setMessage(getString(R.string.alert_dialog_message, t.message))
    if (finish) {
      builder.setNegativeButton(R.string.quit) { _, _ -> finish() }
    }
    val dialog = builder.create()
    dialog.setCancelable(cancelable)
    dialog.show()
  }

  protected fun addHeaderEvent(message: String) {
    events.add(EventModel(EventModel.TYPE_HEADER, message))
    updateList()
    Timber.d("Header: %s", message)
  }

  protected fun addActionEvent(message: String) {
    events.add(EventModel(EventModel.TYPE_ACTION, message))
    updateList()
    Timber.d("Action: %s", message)
  }

  protected fun addResultEvent(message: String) {
    events.add(EventModel(EventModel.TYPE_RESULT, message))
    updateList()
    Timber.d("Result: %s", message)
  }

  private fun updateList() {
    CoroutineScope(Dispatchers.Main).launch {
      adapter.notifyDataSetChanged()
      adapter.notifyItemInserted(events.lastIndex)
      binding.eventRecyclerView.smoothScrollToPosition(events.size - 1)
    }
  }

  // Card Reader Events
  override fun onReaderEvent(readerEvent: CardReaderEvent?) {
    addResultEvent("New ReaderEvent received : ${readerEvent?.type?.name}")
    when (readerEvent?.type) {
      CardReaderEvent.Type.CARD_MATCHED -> {
        addResultEvent("Protocol: ${cardReader.currentProtocol}")
        addResultEvent("Card detected with AID: ${CalypsoClassicInfo.AID}")
        runCardReadTransaction(readerEvent.scheduledCardSelectionsResponse)
      }
      CardReaderEvent.Type.CARD_INSERTED -> {
        addResultEvent("Protocol: ${cardReader.currentProtocol}")
        addResultEvent("Card detected but AID didn't match with ${CalypsoClassicInfo.AID}")
        (cardReader as ObservableCardReader).finalizeCardProcessing()
      }
      CardReaderEvent.Type.CARD_REMOVED -> {
        addResultEvent("Card removed")
      }
      else -> {
        // Do nothing
      }
    }
  }

  override fun onReaderObservationError(pluginName: String?, readerName: String?, e: Throwable?) {
    Timber.e(e, "--Reader Observation Exception %s: %s", pluginName, readerName)
  }

  // Card Transaction Configuration and Processing
  private fun configureCalypsoTransaction() {
    addActionEvent("Prepare Calypso Card Selection with AID: ${CalypsoClassicInfo.AID}")
    try {
      cardSelectionManager = smartCardService?.readerApiFactory!!.createCardSelectionManager()!!

      val isoCardSelector =
          smartCardService
              ?.readerApiFactory
              ?.createIsoCardSelector()
              ?.filterByDfName(CalypsoClassicInfo.AID)

      calypsoCardExtensionProvider = CalypsoExtensionService.getInstance()
      smartCardService?.checkCardExtension(calypsoCardExtensionProvider)

      val calypsoCardSelection =
          calypsoCardExtensionProvider.calypsoCardApiFactory.createCalypsoCardSelectionExtension()

      calypsoCardSelection.prepareReadRecord(
          CalypsoClassicInfo.SFI_EnvironmentAndHolder, CalypsoClassicInfo.RECORD_NUMBER_1)

      cardSelectionManager.prepareSelection(isoCardSelector, calypsoCardSelection)
      cardSelectionManager.scheduleCardSelectionScenario(cardReader as ObservableCardReader, ALWAYS)

      addActionEvent("Waiting for card presentation")
    } catch (e: Exception) {
      Timber.e(e)
      addResultEvent("Exception: ${e.message}")
    }
  }

  private fun runCardReadTransaction(selectionsResponse: ScheduledCardSelectionsResponse) {
    GlobalScope.launch(Dispatchers.IO) {
      try {
        addActionEvent("Process selection")
        val selectionsResult =
            cardSelectionManager.parseScheduledCardSelectionsResponse(selectionsResponse)

        addResultEvent("Calypso card selection: SUCCESS")
        val calypsoCard = selectionsResult.activeSmartCard as CalypsoCard
        addResultEvent("DFNAME: ${HexUtil.toHex(calypsoCard.dfName)}")

        val efEnvironmentHolder =
            calypsoCard.getFileBySfi(CalypsoClassicInfo.SFI_EnvironmentAndHolder)
        addActionEvent("Read environment and holder data")

        addResultEvent(
            "Environment and Holder file: ${HexUtil.toHex(efEnvironmentHolder.data.content)}")

        addHeaderEvent("2nd card exchange: read the event log file")

        addActionEvent("Create card secured transaction with SAM")
        val cryptoCardTransactionManagerFactory =
            LegacySamExtensionService.getInstance()
                .getLegacySamApiFactory()
                .createSymmetricCryptoCardTransactionManagerFactory(samReader, legacySam)
        val securitySetting =
            calypsoCardExtensionProvider.calypsoCardApiFactory.createSymmetricCryptoSecuritySetting(
                cryptoCardTransactionManagerFactory)

        val cardTransactionManager =
            calypsoCardExtensionProvider.calypsoCardApiFactory
                .createSecureRegularModeTransactionManager(cardReader, calypsoCard, securitySetting)

        cardTransactionManager.prepareReadRecords(
            CalypsoClassicInfo.SFI_EventLog,
            CalypsoClassicInfo.RECORD_NUMBER_1,
            CalypsoClassicInfo.RECORD_NUMBER_1,
            CalypsoClassicInfo.RECORD_SIZE)

        cardTransactionManager.prepareReadRecords(
            CalypsoClassicInfo.SFI_Counter1,
            CalypsoClassicInfo.RECORD_NUMBER_1,
            CalypsoClassicInfo.RECORD_NUMBER_1,
            CalypsoClassicInfo.RECORD_SIZE)

        addActionEvent("Process card Command for counter and event logs reading")

        addActionEvent("Process card Opening session for transactions")
        (cardTransactionManager as SecureRegularModeTransactionManager)
            .prepareOpenSecureSession(WriteAccessLevel.LOAD)
            .processCommands(KEEP_OPEN)
        addResultEvent("Opening session: SUCCESS")

        val counter =
            calypsoCard
                .getFileBySfi(CalypsoClassicInfo.SFI_Counter1)
                .data
                .getContentAsCounterValue(CalypsoClassicInfo.RECORD_NUMBER_1)
        val eventLog =
            HexUtil.toHex(calypsoCard.getFileBySfi(CalypsoClassicInfo.SFI_EventLog).data.content)

        addActionEvent("Process card Closing session")
        cardTransactionManager.prepareCloseSecureSession().processCommands(CLOSE_AFTER)
        addResultEvent("Closing session: SUCCESS")

        addResultEvent("Counter value: $counter")
        addResultEvent("EventLog file: $eventLog")

        addResultEvent("End of the Calypso card processing.")
        addResultEvent("You can remove the card now")
      } catch (e: Exception) {
        Timber.e(e)
        addResultEvent("Exception: ${e.message}")
      } finally {
        (cardReader as ObservableCardReader).finalizeCardProcessing()
      }
    }
  }

  override fun onRequestPermissionsResult(
      requestCode: Int,
      permissions: Array<out String>,
      grantResults: IntArray
  ) {
    super.onRequestPermissionsResult(requestCode, permissions, grantResults)
    when (requestCode) {
      PermissionHelper.MY_PERMISSIONS_REQUEST_ALL -> {
        val storagePermissionGranted =
            grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED
        if (!storagePermissionGranted) {
          PermissionDeniedDialog().apply {
            show(supportFragmentManager, PermissionDeniedDialog::class.java.simpleName)
          }
        }
        return
      }
      else -> {
        // Ignore all other requests.
      }
    }
  }
}
