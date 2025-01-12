package com.yaros.RadioUrl.core.dialogs

import android.content.Context
import android.text.method.ScrollingMovementMethod
import android.view.LayoutInflater
import android.view.View
import android.widget.TextView
import androidx.core.view.isGone
import androidx.core.view.isVisible
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.yaros.RadioUrl.R


class ErrorDialog {

    fun show(
        context: Context,
        errorTitle: Int,
        errorMessage: Int,
        errorDetails: String = String()
    ) {
        val builder = MaterialAlertDialogBuilder(context)

        builder.setTitle(context.getString(errorTitle))

        val inflater: LayoutInflater = LayoutInflater.from(context)
        val view: View = inflater.inflate(R.layout.dialog_generic_with_details, null)
        val errorMessageView: TextView = view.findViewById(R.id.dialog_message)
        val errorDetailsLinkView: TextView = view.findViewById(R.id.dialog_details_link)
        val errorDetailsView: TextView = view.findViewById(R.id.dialog_details)

        builder.setView(view)

        val detailsNotEmpty = errorDetails.isNotEmpty()
        errorDetailsLinkView.isVisible = detailsNotEmpty

        if (detailsNotEmpty) {
            errorDetailsView.movementMethod = ScrollingMovementMethod()

            errorDetailsLinkView.setOnClickListener {
                when (errorDetailsView.visibility) {
                    View.GONE -> errorDetailsView.isVisible = true
                    View.VISIBLE -> errorDetailsView.isGone = true
                    View.INVISIBLE -> {
                        return@setOnClickListener
                    }
                }
            }
            errorDetailsView.text = errorDetails
        }

        errorMessageView.text = context.getString(errorMessage)

        builder.setPositiveButton(R.string.dialog_generic_button_okay) { _, _ ->
        }

        builder.show()
    }
}
