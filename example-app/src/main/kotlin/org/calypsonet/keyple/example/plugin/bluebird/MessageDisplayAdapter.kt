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
package org.calypsonet.keyple.example.plugin.bluebird

import android.view.LayoutInflater
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import org.calypsonet.keyple.example.plugin.bluebird.databinding.CardActionMessageBinding
import org.calypsonet.keyple.example.plugin.bluebird.databinding.CardHeaderMessageBinding
import org.calypsonet.keyple.example.plugin.bluebird.databinding.CardResultMessageBinding

class MessageDisplayAdapter(private val messages: List<Message>) :
    RecyclerView.Adapter<MessageDisplayAdapter.MessageViewHolder>() {

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MessageViewHolder {
    val inflater = LayoutInflater.from(parent.context)
    return when (MessageType.values()[viewType]) {
      MessageType.EVENT ->
          MessageViewHolder(CardHeaderMessageBinding.inflate(inflater, parent, false))
      MessageType.ACTION ->
          MessageViewHolder(CardActionMessageBinding.inflate(inflater, parent, false))
      MessageType.RESULT ->
          MessageViewHolder(CardResultMessageBinding.inflate(inflater, parent, false))
    }
  }

  override fun getItemCount(): Int = messages.size

  override fun onBindViewHolder(holder: MessageViewHolder, position: Int) {
    holder.bind(messages[position])
  }

  override fun getItemViewType(position: Int): Int = messages[position].type.ordinal

  class MessageViewHolder : RecyclerView.ViewHolder {
    private var binding: Any? = null
    private val textView: TextView

    constructor(binding: CardActionMessageBinding) : super(binding.root) {
      this.binding = binding
      textView = binding.cardActionTextView
    }

    constructor(binding: CardResultMessageBinding) : super(binding.root) {
      this.binding = binding
      textView = binding.cardResultTextView
    }

    constructor(binding: CardHeaderMessageBinding) : super(binding.root) {
      this.binding = binding
      textView = binding.cardHeaderTextView
    }

    fun bind(message: Message) {
      textView.text = message.text
    }
  }

  class Message(val type: MessageType, val text: String)

  enum class MessageType {
    EVENT,
    ACTION,
    RESULT,
  }
}
