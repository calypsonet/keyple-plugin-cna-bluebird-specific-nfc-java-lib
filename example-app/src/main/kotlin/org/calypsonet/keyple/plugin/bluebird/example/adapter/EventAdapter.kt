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
package org.calypsonet.keyple.plugin.bluebird.example.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.RadioButton
import androidx.recyclerview.widget.RecyclerView
import org.calypsonet.keyple.plugin.bluebird.example.R
import org.calypsonet.keyple.plugin.bluebird.example.databinding.CardActionEventBinding
import org.calypsonet.keyple.plugin.bluebird.example.databinding.CardChoiceEventBinding
import org.calypsonet.keyple.plugin.bluebird.example.databinding.CardHeaderEventBinding
import org.calypsonet.keyple.plugin.bluebird.example.databinding.CardResultEventBinding
import org.calypsonet.keyple.plugin.bluebird.example.model.ChoiceEventModel
import org.calypsonet.keyple.plugin.bluebird.example.model.EventModel
import org.calypsonet.keyple.plugin.bluebird.example.util.getColorResource

class EventAdapter(private val events: ArrayList<EventModel>) :
    RecyclerView.Adapter<EventAdapter.BaseViewHolder>() {

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): BaseViewHolder {
    val inflater = LayoutInflater.from(parent.context)
    return when (viewType) {
      EventModel.TYPE_ACTION ->
          ActionViewHolder(CardActionEventBinding.inflate(inflater, parent, false))
      EventModel.TYPE_RESULT ->
          ResultViewHolder(CardResultEventBinding.inflate(inflater, parent, false))
      EventModel.TYPE_MULTICHOICE ->
          ChoiceViewHolder(CardChoiceEventBinding.inflate(inflater, parent, false))
      else -> HeaderViewHolder(CardHeaderEventBinding.inflate(inflater, parent, false))
    }
  }

  override fun getItemCount(): Int = events.size

  override fun onBindViewHolder(holder: BaseViewHolder, position: Int) {
    holder.bind(events[position])
  }

  override fun getItemViewType(position: Int): Int = events[position].type

  abstract class BaseViewHolder(private val binding: Any) :
      RecyclerView.ViewHolder(
          when (binding) {
            is CardActionEventBinding -> binding.root
            is CardResultEventBinding -> binding.root
            is CardHeaderEventBinding -> binding.root
            is CardChoiceEventBinding -> binding.root
            else -> throw IllegalArgumentException("Unknown binding type")
          }) {
    abstract fun bind(event: EventModel)
  }

  class ActionViewHolder(private val binding: CardActionEventBinding) : BaseViewHolder(binding) {
    override fun bind(event: EventModel) {
      binding.cardActionTextView.text = event.text
    }
  }

  class ResultViewHolder(private val binding: CardResultEventBinding) : BaseViewHolder(binding) {
    override fun bind(event: EventModel) {
      binding.cardActionTextView.text = event.text
    }
  }

  class HeaderViewHolder(private val binding: CardHeaderEventBinding) : BaseViewHolder(binding) {
    override fun bind(event: EventModel) {
      binding.cardActionTextView.text = event.text
    }
  }

  class ChoiceViewHolder(private val binding: CardChoiceEventBinding) : BaseViewHolder(binding) {
    override fun bind(event: EventModel) {
      binding.cardActionTextView.text = event.text
      binding.choiceRadioGroup.removeAllViews()

      if (event is ChoiceEventModel) {
        event.choices.forEachIndexed { index, choice ->
          val button =
              RadioButton(itemView.context).apply {
                text = choice
                id = index
                setOnClickListener { event.callback(choice) }
                setTextColor(context.getColorResource(R.color.textColorPrimary))
              }
          binding.choiceRadioGroup.addView(button)
        }
      }
    }
  }
}
