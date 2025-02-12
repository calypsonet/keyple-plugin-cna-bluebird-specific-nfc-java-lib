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

import android.os.Bundle
import android.view.WindowManager
import androidx.appcompat.app.ActionBarDrawerToggle
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.widget.Toolbar
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.navigation.NavigationView
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import org.calypsonet.keyple.plugin.bluebird.example.R
import org.calypsonet.keyple.plugin.bluebird.example.adapter.EventAdapter
import org.calypsonet.keyple.plugin.bluebird.example.databinding.ActivityMainBinding
import org.calypsonet.keyple.plugin.bluebird.example.model.EventModel
import org.calypsonet.keyple.plugin.bluebird.example.util.CalypsoClassicInfo.SAM_PROFILE_NAME
import org.eclipse.keypop.calypso.card.WriteAccessLevel
import org.eclipse.keypop.calypso.crypto.legacysam.sam.CalypsoSam
import org.eclipse.keypop.calypso.card.transaction.CardSecuritySetting
import org.eclipse.keypop.reader.CardReader
import org.eclipse.keypop.reader.CardReaderEvent
import org.eclipse.keypop.reader.ConfigurableCardReader
import org.eclipse.keypop.reader.spi.CardReaderObservationExceptionHandlerSpi
import org.eclipse.keypop.reader.spi.CardReaderObserverSpi
import org.eclipse.keyple.card.calypso.CalypsoExtensionService
import org.eclipse.keyple.core.service.Plugin
import org.eclipse.keyple.core.service.resource.CardResourceProfileConfigurator
import org.eclipse.keyple.core.service.resource.CardResourceService
import org.eclipse.keyple.core.service.resource.CardResourceServiceProvider
import org.eclipse.keyple.core.service.resource.PluginsConfigurator
import org.eclipse.keyple.core.service.resource.spi.ReaderConfiguratorSpi
import timber.log.Timber

abstract class AbstractExampleActivity :
    AppCompatActivity(),
    NavigationView.OnNavigationItemSelectedListener,
    CardReaderObserverSpi,
    CardReaderObservationExceptionHandlerSpi {

  protected lateinit var cardReader: ConfigurableCardReader
  protected lateinit var samReader: CardReader
  protected lateinit var binding: ActivityMainBinding

  /** Use to modify event update behaviour regarding current use case execution */
  interface UseCase {
    fun onEventUpdate(event: CardReaderEvent?)
  }

  /** Variables for event window */
  private lateinit var adapter: RecyclerView.Adapter<*>
  private lateinit var layoutManager: RecyclerView.LayoutManager
  private val events = arrayListOf<EventModel>()

  protected var useCase: UseCase? = null

  protected lateinit var calypsoCardExtensionProvider: CalypsoExtensionService

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)
    binding = ActivityMainBinding.inflate(layoutInflater)
    setContentView(binding.root)
    initContentView()

    /** Init recycler view */
    adapter = EventAdapter(events)
    layoutManager = LinearLayoutManager(this)
    binding.eventRecyclerView.layoutManager = layoutManager
    binding.eventRecyclerView.adapter = adapter

    /** Init menu */
    binding.navigationView.setNavigationItemSelectedListener(this)
    val toggle =
        ActionBarDrawerToggle(
            this,
            binding.drawerLayout,
            binding.toolbar,
            R.string.open_navigation_drawer,
            R.string.close_navigation_drawer)
    binding.drawerLayout.addDrawerListener(toggle)
    toggle.syncState()

    window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
  }

  protected fun initActionBar(toolbar: Toolbar, title: String, subtitle: String) {
    setSupportActionBar(toolbar)
    val actionBar = supportActionBar
    actionBar?.title = title
    actionBar?.subtitle = subtitle
  }

  protected fun showAlertDialog(t: Throwable, finish: Boolean = false, cancelable: Boolean = true) {
    val builder = AlertDialog.Builder(this@AbstractExampleActivity)
    builder.setTitle(R.string.alert_dialog_title)
    builder.setMessage(getString(R.string.alert_dialog_message, t.message))
    if (finish) {
      builder.setNegativeButton(R.string.quit) { _, _ -> finish() }
    }
    val dialog = builder.create()
    dialog.setCancelable(cancelable)
    dialog.show()
  }

  protected fun clearEvents() {
    events.clear()
    adapter.notifyDataSetChanged()
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

  abstract fun initContentView()

  abstract fun initReaders()

  protected fun getSecuritySettings(): CardSecuritySetting? {
    // The default KIF values for personalization, loading and debiting
    val DEFAULT_KIF_PERSO = 0x21.toByte()
    val DEFAULT_KIF_LOAD = 0x27.toByte()
    val DEFAULT_KIF_DEBIT = 0x30.toByte()

    val samCardResourceExtension = CalypsoExtensionService.getInstance()
    samCardResourceExtension.createCardSecuritySetting()

    // Create security settings that reference the same SAM profile requested from the card resource
    // service and enable the multiple session mode.
    val samResource = CardResourceServiceProvider.getService().getCardResource(SAM_PROFILE_NAME)

    return CalypsoExtensionService.getInstance()
        .createCardSecuritySetting()
        .setControlSamResource(samResource.reader, samResource.smartCard as CalypsoSam)
        .assignDefaultKif(WriteAccessLevel.PERSONALIZATION, DEFAULT_KIF_PERSO)
        .assignDefaultKif(WriteAccessLevel.LOAD, DEFAULT_KIF_LOAD)
        .assignDefaultKif(WriteAccessLevel.DEBIT, DEFAULT_KIF_DEBIT)
        .enableMultipleSession()
  }

  /** Setup the [CardResourceService] to provide a Calypso SAM C1 resource when requested. */
  open fun setupCardResourceService(
      plugin: Plugin,
      readerNameRegex: String?,
      samProfileName: String?
  ) {
    // Create a card resource extension expecting a SAM "C1".
    val samSelection =
        CalypsoExtensionService.getInstance()
            .createSamSelection()
            .filterByProductType(CalypsoSam.ProductType.SAM_C1)

    val samCardResourceExtension =
        CalypsoExtensionService.getInstance().createSamResourceProfileExtension(samSelection)

    // Get the service
    val cardResourceService = CardResourceServiceProvider.getService()

    // configure and start the card resource service
    cardResourceService!!
        .configurator
        .withPlugins(PluginsConfigurator.builder().addPlugin(plugin, ReaderConfigurator()).build())
        .withCardResourceProfiles(
            CardResourceProfileConfigurator.builder(samProfileName, samCardResourceExtension)
                .withReaderNameRegex(readerNameRegex)
                .build())
        .configure()

    cardResourceService.start()

    // verify the resource availability
    val cardResource =
        cardResourceService.getCardResource(samProfileName)
            ?: throw IllegalStateException(
                java.lang.String.format(
                    "Unable to retrieve a SAM card resource for profile '%s' from reader '%s' in plugin '%s'",
                    samProfileName,
                    readerNameRegex,
                    plugin.name))

    // release the resource
    cardResourceService.releaseCardResource(cardResource)
  }

  internal class ReaderConfigurator : ReaderConfiguratorSpi {
    override fun setupReader(reader: CardReader) {
      Timber.d("Nothing to configure for reader '%s'", reader.name)
    }
  }

  override fun onReaderObservationError(pluginName: String?, readerName: String?, e: Throwable?) {
    Timber.e(e, "--Reader Observation Exception %s: %s", pluginName, readerName)
  }
}
