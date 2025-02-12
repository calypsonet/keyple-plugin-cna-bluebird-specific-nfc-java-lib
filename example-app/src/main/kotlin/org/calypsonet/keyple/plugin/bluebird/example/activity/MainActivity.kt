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
package org.calypsonet.keyple.plugin.bluebird.example.activity

import android.Manifest
import android.app.ProgressDialog
import android.content.pm.PackageManager
import android.view.MenuItem
import androidx.core.view.GravityCompat
import java.util.concurrent.atomic.AtomicBoolean
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
import org.eclipse.keypop.calypso.card.WriteAccessLevel
import org.eclipse.keypop.calypso.card.card.CalypsoCard
import org.eclipse.keypop.reader.CardReaderEvent
import org.eclipse.keypop.reader.ConfigurableCardReader
import org.eclipse.keypop.reader.ObservableCardReader
import org.eclipse.keypop.reader.selection.CardSelectionManager
import org.eclipse.keypop.reader.selection.ScheduledCardSelectionsResponse
import org.eclipse.keyple.card.calypso.CalypsoExtensionService
import org.eclipse.keyple.core.common.KeyplePluginExtensionFactory
import org.eclipse.keyple.core.service.*
import org.eclipse.keyple.core.util.HexUtil
import timber.log.Timber

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
    initActionBar(binding.toolbar, "Keyple demo", "Bluebird Plugin")
  }

  override fun onResume() {
    super.onResume()

    progress = ProgressDialog(this)
    progress.setMessage(getString(R.string.please_wait))
    progress.setCancelable(false)

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

  override fun initReaders() {
    Timber.d("initReaders")
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

      smartCardService = SmartCardServiceProvider.getService()
      val bluebirdPlugin = smartCardService!!.registerPlugin(pluginFactory)
      cardReader =
          bluebirdPlugin.getReader(BluebirdContactlessReader.READER_NAME) as ConfigurableCardReader

      (cardReader as ObservableCardReader).setReaderObservationExceptionHandler {
          pluginName,
          readerName,
          e ->
        Timber.e("An unexpected reader error occurred: $pluginName:$readerName : $e")
      }

      (cardReader as ObservableCardReader).addObserver(this@MainActivity)

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
          arrayOf(
              Manifest.permission.READ_EXTERNAL_STORAGE, BluebirdPlugin.BLUEBIRD_SAM_PERMISSION))

      setupCardResourceService(
          bluebirdPlugin,
          CalypsoClassicInfo.SAM_READER_NAME_REGEX,
          CalypsoClassicInfo.SAM_PROFILE_NAME)

      areReadersInitialized.set(true)

      (cardReader as ObservableCardReader).startCardDetection(
          ObservableCardReader.DetectionMode.REPEATING)

      withContext(Dispatchers.Main) { progress.dismiss() }
    }
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

  override fun onBackPressed() {
    if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
      binding.drawerLayout.closeDrawer(GravityCompat.START)
    } else {
      super.onBackPressed()
    }
  }

  override fun onNavigationItemSelected(item: MenuItem): Boolean {
    if (binding.drawerLayout.isDrawerOpen(GravityCompat.START)) {
      binding.drawerLayout.closeDrawer(GravityCompat.START)
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

      /* Prepare the reading order and keep the associated parser for later use once the selection has been made. */
      calypsoCardSelection.prepareReadRecord(
          CalypsoClassicInfo.SFI_EnvironmentAndHolder, CalypsoClassicInfo.RECORD_NUMBER_1)

      /* Add the selection case to the current selection */
      cardSelectionManager.prepareSelection(calypsoCardSelection)

      /* Provide the SeReader with the selection operation to be processed when a card is inserted. */
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
                    addResultEvent("Protocol: ${cardReader.currentProtocol}")
                    addResultEvent("Card detected with AID: ${CalypsoClassicInfo.AID}")
                    responseProcessor(event.scheduledCardSelectionsResponse)
                    (cardReader as ObservableCardReader).finalizeCardProcessing()
                  }
                  CardReaderEvent.Type.CARD_INSERTED -> {
                    addResultEvent("Protocol: ${cardReader.currentProtocol}")
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
            }
          }

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
            "Environment and Holder file: ${
                        HexUtil.toHex(
                            efEnvironmentHolder.data.content
                        )
                    }")

        addHeaderEvent("2nd card exchange: read the event log file")

        val cardTransactionManager =
            if (withSam) {
              addActionEvent("Create card secured transaction with SAM")
              calypsoCardExtensionProvider.createCardTransaction(
                  cardReader, calypsoCard, getSecuritySettings())
            } else {
              calypsoCardExtensionProvider.createCardTransactionWithoutSecurity(
                  cardReader, calypsoCard)
            }

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

        if (withSam) {
          addActionEvent("Process card Opening session for transactions")
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

          addResultEvent("Counter value: $counter")
          addResultEvent("EventLog file: $eventLog")
        } else {
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
          val cardTransactionManager =
              calypsoCardExtensionProvider.createCardTransaction(
                  cardReader, calypsoCard, getSecuritySettings())

          when (transactionType) {
            TransactionType.INCREASE -> {
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
              addActionEvent("Process card Opening session for transactions")
              cardTransactionManager.processOpening(WriteAccessLevel.DEBIT)
              addResultEvent("Opening session: SUCCESS")

              cardTransactionManager.prepareReadRecords(
                  CalypsoClassicInfo.SFI_Counter1,
                  CalypsoClassicInfo.RECORD_NUMBER_1,
                  CalypsoClassicInfo.RECORD_NUMBER_1,
                  CalypsoClassicInfo.RECORD_SIZE)
              cardTransactionManager.processCommands()

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
