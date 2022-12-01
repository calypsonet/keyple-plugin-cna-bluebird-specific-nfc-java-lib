/* **************************************************************************************
 * Copyright (c) 2021 Calypso Networks Association https://calypsonet.org/
 *
 * See the NOTICE file(s) distributed with this work for additional information
 * regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the terms of the
 * Eclipse Public License 2.0 which is available at http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ************************************************************************************** */
package org.calypsonet.keyple.plugin.bluebird.example.activity

import android.Manifest
import android.app.ProgressDialog
import android.content.pm.PackageManager
import android.view.MenuItem
import androidx.core.view.GravityCompat
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.android.synthetic.main.activity_main.drawerLayout
import kotlinx.android.synthetic.main.activity_main.toolbar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.calypsonet.keyple.plugin.bluebird.BluebirdContactReader
import org.calypsonet.keyple.plugin.bluebird.BluebirdContactlessReader
import org.calypsonet.keyple.plugin.bluebird.BluebirdPlugin
import org.calypsonet.keyple.plugin.bluebird.BluebirdPluginFactoryProvider
import org.calypsonet.keyple.plugin.bluebird.BluebirdSupportContactlessProtocols
import org.calypsonet.keyple.plugin.bluebird.example.R
import org.calypsonet.keyple.plugin.bluebird.example.dialog.PermissionDeniedDialog
import org.calypsonet.keyple.plugin.bluebird.example.util.CalypsoClassicInfo
import org.calypsonet.keyple.plugin.bluebird.example.util.PermissionHelper
import org.calypsonet.terminal.calypso.WriteAccessLevel
import org.calypsonet.terminal.calypso.card.CalypsoCard
import org.calypsonet.terminal.reader.CardReaderEvent
import org.calypsonet.terminal.reader.ConfigurableCardReader
import org.calypsonet.terminal.reader.ObservableCardReader
import org.calypsonet.terminal.reader.selection.CardSelectionManager
import org.calypsonet.terminal.reader.selection.ScheduledCardSelectionsResponse
import org.eclipse.keyple.card.calypso.CalypsoExtensionService
import org.eclipse.keyple.core.common.KeyplePluginExtensionFactory
import org.eclipse.keyple.core.service.*
import org.eclipse.keyple.core.util.HexUtil
import timber.log.Timber

/** Activity launched on app start up that display the only screen available on this example app. */
class MainActivity : AbstractExampleActivity() {

  private var smartCardService: SmartCardService? = null
  private lateinit var cardSelectionManager: CardSelectionManager

  private val areReadersInitialized = AtomicBoolean(false)

  private lateinit var progress: ProgressDialog

  private enum class TransactionType {
    DECREASE,
    INCREASE
  }

  override fun initContentView() {
    setContentView(R.layout.activity_main)
    initActionBar(toolbar, "Keyple demo", "Bluebird Plugin")
  }

  /** Called when the activity (screen) is first displayed or resumed from background */
  override fun onResume() {
    super.onResume()

    progress = ProgressDialog(this)
    progress.setMessage(getString(R.string.please_wait))
    progress.setCancelable(false)

    // Check whether readers are already initialized (return from background) or not (first launch)
    if (!areReadersInitialized.get()) {
      addActionEvent("Enabling NFC Reader mode")
      addResultEvent("Please choose a use case")
      progress.show()
      initReaders()
    } else {
      addActionEvent("Start card Read Write Mode")
      (cardReader as ObservableCardReader).startCardDetection(
          ObservableCardReader.DetectionMode.REPEATING)
    }
  }

  /** Initializes the card reader (Contactless Reader) and SAM reader (Contact Reader) */
  override fun initReaders() {
    Timber.d("initReaders")
    // Connection to Bluebird lib takes time, we've added a callback to this factory.
    GlobalScope.launch {
      val pluginFactory: KeyplePluginExtensionFactory?
      try {
        pluginFactory =
            withContext(Dispatchers.IO) {
              BluebirdPluginFactoryProvider.getFactory(this@MainActivity)
            }
      } catch (e: Exception) {
        withContext(Dispatchers.Main) { showAlertDialog(e, finish = true, cancelable = false) }
        return@launch
      }

      // Get the instance of the SmartCardService (Singleton pattern)
      smartCardService = SmartCardServiceProvider.getService()

      // Register the Bluebird with SmartCardService, get the corresponding generic Plugin in return
      val bluebirdPlugin = smartCardService!!.registerPlugin(pluginFactory)

      // Get and configure the card reader
      cardReader =
          bluebirdPlugin.getReader(BluebirdContactlessReader.READER_NAME) as ConfigurableCardReader

      (cardReader as ObservableCardReader).setReaderObservationExceptionHandler {
          pluginName,
          readerName,
          e ->
        Timber.e("An unexpected reader error occurred: $pluginName:$readerName : $e")
      }

      // Set the current activity as Observer of the card reader
      (cardReader as ObservableCardReader).addObserver(this@MainActivity)

      // Set the VASUP terminal identification data
      bluebirdPlugin
          .getReaderExtension(
              BluebirdContactlessReader::class.java, BluebirdContactlessReader.READER_NAME)
          .setSkyEcpVasupPayload(HexUtil.toByteArray(CalypsoClassicInfo.VASUP_PAYLOAD))

      // Activate protocols for the card reader: B, B' and SKY ECP B
      cardReader.activateProtocol(
          BluebirdSupportContactlessProtocols.ISO_14443_4_B.name, "ISO_14443_4_CARD")
      cardReader.activateProtocol(
          BluebirdSupportContactlessProtocols.INNOVATRON_B_PRIME.name, "INNOVATRON_B_PRIME_CARD")
      cardReader.activateProtocol(
          BluebirdSupportContactlessProtocols.ISO14443_4_SKY_ECP_B.name, "ISO_14443_4_CARD")

      // Get and configure the SAM reader
      samReader = bluebirdPlugin.getReader(BluebirdContactReader.READER_NAME)

      PermissionHelper.checkPermission(
          this@MainActivity,
          arrayOf(
              Manifest.permission.READ_EXTERNAL_STORAGE, BluebirdPlugin.BLUEBIRD_SAM_PERMISSION))

      setupCardResourceService(
          bluebirdPlugin,
          CalypsoClassicInfo.SAM_READER_NAME_REGEX,
          CalypsoClassicInfo.SAM_PROFILE_NAME)

      areReadersInitialized.set(true)

      // Start the NFC detection
      (cardReader as ObservableCardReader).startCardDetection(
          ObservableCardReader.DetectionMode.REPEATING)

      withContext(Dispatchers.Main) { progress.dismiss() }
    }
  }

  /** Called when the activity (screen) is destroyed or put in background */
  override fun onPause() {
    if (areReadersInitialized.get()) {
      addActionEvent("Stopping card Read Write Mode")
      // Stop NFC card detection
      (cardReader as ObservableCardReader).stopCardDetection()
    }
    super.onPause()
  }

  /** Called when the activity (screen) is destroyed */
  override fun onDestroy() {
    cardReader.let {
      // stop propagating the reader events
      (cardReader as ObservableCardReader).removeObserver(this)
    }

    // Unregister the Bluebird plugin
    smartCardService?.plugins?.forEach { smartCardService?.unregisterPlugin(it.name) }

    super.onDestroy()
  }

  override fun onBackPressed() {
    if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
      drawerLayout.closeDrawer(GravityCompat.START)
    } else {
      super.onBackPressed()
    }
  }

  override fun onNavigationItemSelected(item: MenuItem): Boolean {
    if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
      drawerLayout.closeDrawer(GravityCompat.START)
    }
    when (item.itemId) {
      R.id.usecase1 -> {
        clearEvents()
        addHeaderEvent("Running Calypso Read transaction (without SAM)")
        configureCalypsoTransaction(::runCardReadTransactionWithoutSam)
      }
      R.id.usecase2 -> {
        clearEvents()
        addHeaderEvent("Running Calypso Read transaction (with SAM)")
        configureCalypsoTransaction(::runCardReadTransactionWithSam)
      }
      R.id.usecase3 -> {
        clearEvents()
        addHeaderEvent("Running Calypso Read/Write transaction")
        configureCalypsoTransaction(::runCardReadWriteIncreaseTransaction)
      }
      R.id.usecase4 -> {
        clearEvents()
        addHeaderEvent("Running Calypso Read/Write transaction")
        configureCalypsoTransaction(::runCardReadWriteDecreaseTransaction)
      }
    }
    return true
  }

  override fun onReaderEvent(readerEvent: CardReaderEvent?) {
    addResultEvent("New ReaderEvent received : ${readerEvent?.type?.name}")
    useCase?.onEventUpdate(readerEvent)
  }

  private fun configureCalypsoTransaction(
      responseProcessor: (selectionsResponse: ScheduledCardSelectionsResponse) -> Unit
  ) {
    addActionEvent("Prepare Calypso Card Selection with AID: ${CalypsoClassicInfo.AID}")
    try {
      /* Prepare a Calypso Card selection */
      cardSelectionManager = smartCardService?.createCardSelectionManager()!!

      /* Calypso selection: configures a CardSelection with all the desired attributes to make the selection and read additional information afterwards */
      calypsoCardExtensionProvider = CalypsoExtensionService.getInstance()

      smartCardService?.checkCardExtension(calypsoCardExtensionProvider)

      val calypsoCardSelection = calypsoCardExtensionProvider.createCardSelection()
      calypsoCardSelection.filterByDfName(CalypsoClassicInfo.AID)

      /* Prepare the reading order and keep the associated parser for later use once the
      selection has been made. */
      calypsoCardSelection.prepareReadRecord(
          CalypsoClassicInfo.SFI_EnvironmentAndHolder, CalypsoClassicInfo.RECORD_NUMBER_1)

      /*
       * Add the selection case to the current selection (we could have added other cases
       * here)
       */
      cardSelectionManager.prepareSelection(calypsoCardSelection)

      /*
       * Provide the SeReader with the selection operation to be processed when a card is
       * inserted.
       */
      cardSelectionManager.scheduleCardSelectionScenario(
          cardReader as ObservableCardReader,
          ObservableCardReader.DetectionMode.REPEATING,
          ObservableCardReader.NotificationMode.ALWAYS)

      useCase =
          object : UseCase {
            override fun onEventUpdate(event: CardReaderEvent?) {
              CoroutineScope(Dispatchers.Main).launch {
                when (event?.type) {
                  CardReaderEvent.Type.CARD_MATCHED -> {
                    addResultEvent("Card detected with AID: ${CalypsoClassicInfo.AID}")
                    responseProcessor(event.scheduledCardSelectionsResponse)
                    (cardReader as ObservableCardReader).finalizeCardProcessing()
                  }
                  CardReaderEvent.Type.CARD_INSERTED -> {
                    addResultEvent(
                        "Card detected but AID didn't match with ${CalypsoClassicInfo.AID}")
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
              // eventRecyclerView.smoothScrollToPosition(events.size - 1)
            }
          }

      // notify reader that se detection has been launched
      addActionEvent("Waiting for card presentation")
    } catch (e: KeyplePluginException) {
      Timber.e(e)
      addResultEvent("Exception: ${e.message}")
    } catch (e: Exception) {
      Timber.e(e)
      addResultEvent("Exception: ${e.message}")
    }
  }

  private fun runCardReadTransactionWithSam(selectionsResponse: ScheduledCardSelectionsResponse) {
    runCardReadTransaction(selectionsResponse, true)
  }

  private fun runCardReadTransactionWithoutSam(
      selectionsResponse: ScheduledCardSelectionsResponse
  ) {
    runCardReadTransaction(selectionsResponse, false)
  }

  private fun runCardReadTransaction(
      selectionsResponse: ScheduledCardSelectionsResponse,
      withSam: Boolean
  ) {

    GlobalScope.launch(Dispatchers.IO) {
      try {
        /*
         * print tag info in View
         */

        addActionEvent("Process selection")
        val selectionsResult =
            cardSelectionManager.parseScheduledCardSelectionsResponse(selectionsResponse)

        addResultEvent("Calypso card selection: SUCCESS")
        val calypsoCard = selectionsResult.activeSmartCard as CalypsoCard
        addResultEvent("DFNAME: ${HexUtil.toHex(calypsoCard.dfName)}")

        /*
         * Retrieve the data read from the parser updated during the selection process
         */
        val efEnvironmentHolder =
            calypsoCard.getFileBySfi(CalypsoClassicInfo.SFI_EnvironmentAndHolder)
        addActionEvent("Read environment and holder data")

        addResultEvent(
            "Environment and Holder file: ${
                            HexUtil.toHex(
                                efEnvironmentHolder.data.content
                            )
                        }")

        addHeaderEvent("2nd card exchange: read the event log file")

        val cardTransactionManager =
            if (withSam) {
              addActionEvent("Create card secured transaction with SAM")

              /*
               * Create card secured transaction.
               */
              calypsoCardExtensionProvider.createCardTransaction(
                  cardReader, calypsoCard, getSecuritySettings())
            } else {
              // Create card unsecured transaction
              calypsoCardExtensionProvider.createCardTransactionWithoutSecurity(
                  cardReader, calypsoCard)
            }

        /*
         * Prepare the reading order and keep the associated parser for later use once the
         * transaction has been processed.
         */
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

        /*
         * Actual card communication: send the prepared read order, then close the channel
         * with the card
         */
        addActionEvent("Process card Command for counter and event logs reading")

        if (withSam) {
          addActionEvent("Process card Opening session for transactions")

          // opens a secure session and processes prepared commands
          cardTransactionManager.processOpening(WriteAccessLevel.LOAD)
          addResultEvent("Opening session: SUCCESS")

          val counter =
              calypsoCard
                  .getFileBySfi(CalypsoClassicInfo.SFI_Counter1)
                  .data
                  .getContentAsCounterValue(CalypsoClassicInfo.RECORD_NUMBER_1)
          val eventLog =
              HexUtil.toHex(calypsoCard.getFileBySfi(CalypsoClassicInfo.SFI_EventLog).data.content)

          addActionEvent("Process card Closing session")
          cardTransactionManager.processClosing()
          addResultEvent("Closing session: SUCCESS")

          // In secured reading, value read elements can only be trusted if the session is closed
          // without error.
          addResultEvent("Counter value: $counter")
          addResultEvent("EventLog file: $eventLog")
        } else {
          // processes prepared commands
          cardTransactionManager.processCommands()

          val counter =
              calypsoCard
                  .getFileBySfi(CalypsoClassicInfo.SFI_Counter1)
                  .data
                  .getContentAsCounterValue(CalypsoClassicInfo.RECORD_NUMBER_1)
          val eventLog =
              HexUtil.toHex(calypsoCard.getFileBySfi(CalypsoClassicInfo.SFI_EventLog).data.content)

          addResultEvent("Counter value: $counter")
          addResultEvent("EventLog file: $eventLog")
        }

        addResultEvent("End of the Calypso card processing.")
        addResultEvent("You can remove the card now")
      } catch (e: KeyplePluginException) {
        Timber.e(e)
        addResultEvent("Exception: ${e.message}")
      } catch (e: Exception) {
        Timber.e(e)
        addResultEvent("Exception: ${e.message}")
      }
    }
  }

  private fun runCardReadWriteIncreaseTransaction(
      selectionsResponse: ScheduledCardSelectionsResponse
  ) {
    runCardReadWriteTransaction(selectionsResponse, TransactionType.INCREASE)
  }

  private fun runCardReadWriteDecreaseTransaction(
      selectionsResponse: ScheduledCardSelectionsResponse
  ) {
    runCardReadWriteTransaction(selectionsResponse, TransactionType.DECREASE)
  }

  private fun runCardReadWriteTransaction(
      selectionsResponse: ScheduledCardSelectionsResponse,
      transactionType: TransactionType
  ) {
    GlobalScope.launch(Dispatchers.IO) {
      try {
        addActionEvent("1st card exchange: aid selection")
        val selectionsResult =
            cardSelectionManager.parseScheduledCardSelectionsResponse(selectionsResponse)

        if (selectionsResult.activeSelectionIndex != -1) {
          addResultEvent("Calypso card selection: SUCCESS")
          val calypsoCard = selectionsResult.activeSmartCard as CalypsoCard
          addResultEvent("DFNAME: ${HexUtil.toHex(calypsoCard.dfName)}")

          addActionEvent("Create card secured transaction with SAM")
          // Create card secured transaction
          val cardTransactionManager =
              calypsoCardExtensionProvider.createCardTransaction(
                  cardReader, calypsoCard, getSecuritySettings())

          when (transactionType) {
            TransactionType.INCREASE -> {
              /*
               * Open Session for the debit key
               */
              addActionEvent("Process card Opening session for transactions")
              cardTransactionManager.processOpening(WriteAccessLevel.LOAD)
              addResultEvent("Opening session: SUCCESS")

              cardTransactionManager.prepareReadRecords(
                  CalypsoClassicInfo.SFI_Counter1,
                  CalypsoClassicInfo.RECORD_NUMBER_1,
                  CalypsoClassicInfo.RECORD_NUMBER_1,
                  CalypsoClassicInfo.RECORD_SIZE)
              cardTransactionManager.processCommands()

              cardTransactionManager.prepareIncreaseCounter(
                  CalypsoClassicInfo.SFI_Counter1, CalypsoClassicInfo.RECORD_NUMBER_1, 10)
              addActionEvent("Process card increase counter by 10")
              cardTransactionManager.processClosing()
              addResultEvent("Increase by 10: SUCCESS")
            }
            TransactionType.DECREASE -> {
              /*
               * Open Session for the debit key
               */
              addActionEvent("Process card Opening session for transactions")
              cardTransactionManager.processOpening(WriteAccessLevel.DEBIT)
              addResultEvent("Opening session: SUCCESS")

              cardTransactionManager.prepareReadRecords(
                  CalypsoClassicInfo.SFI_Counter1,
                  CalypsoClassicInfo.RECORD_NUMBER_1,
                  CalypsoClassicInfo.RECORD_NUMBER_1,
                  CalypsoClassicInfo.RECORD_SIZE)
              cardTransactionManager.processCommands()

              /*
               * A ratification command will be sent (CONTACTLESS_MODE).
               */
              cardTransactionManager.prepareDecreaseCounter(
                  CalypsoClassicInfo.SFI_Counter1, CalypsoClassicInfo.RECORD_NUMBER_1, 1)
              addActionEvent("Process card decreasing counter and close transaction")
              cardTransactionManager.processClosing()
              addResultEvent("Decrease by 1: SUCCESS")
            }
          }

          addResultEvent("End of the Calypso card processing.")
          addResultEvent("You can remove the card now")
        } else {
          addResultEvent(
              "The selection of the card has failed. Should not have occurred due to the MATCHED_ONLY selection mode.")
        }
      } catch (e: KeyplePluginException) {
        Timber.e(e)
        addResultEvent("Exception: ${e.message}")
      } catch (e: Exception) {
        Timber.e(e)
        addResultEvent("Exception: ${e.message}")
      }
    }
  }

  override fun onRequestPermissionsResult(
      requestCode: Int,
      permissions: Array<out String>,
      grantResults: IntArray
  ) {
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
      // Add other 'when' lines to check for other
      // permissions this app might request.
      else -> {
        // Ignore all other requests.
      }
    }
  }
}
