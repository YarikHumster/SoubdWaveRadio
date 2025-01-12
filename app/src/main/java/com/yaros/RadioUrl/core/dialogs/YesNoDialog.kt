package com.yaros.RadioUrl.core.dialogs

import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.yaros.RadioUrl.Keys
import com.yaros.RadioUrl.R


class YesNoDialog(private var yesNoDialogListener: YesNoDialogListener) {

    interface YesNoDialogListener {
        fun onYesNoDialog(type: Int, dialogResult: Boolean, payload: Int, payloadString: String) {
        }
    }

    private lateinit var dialog: AlertDialog


    fun show(
        context: Context,
        type: Int,
        title: Int = Keys.EMPTY_STRING_RESOURCE,
        message: Int,
        yesButton: Int = R.string.dialog_yes_no_positive_button_default,
        noButton: Int = R.string.dialog_generic_button_cancel,
        payload: Int = Keys.DIALOG_EMPTY_PAYLOAD_INT,
        payloadString: String = Keys.DIALOG_EMPTY_PAYLOAD_STRING
    ) {
        show(
            context,
            type,
            title,
            context.getString(message),
            yesButton,
            noButton,
            payload,
            payloadString
        )
    }

    fun show(
        context: Context,
        type: Int,
        title: Int = Keys.EMPTY_STRING_RESOURCE,
        messageString: String,
        yesButton: Int = R.string.dialog_yes_no_positive_button_default,
        noButton: Int = R.string.dialog_generic_button_cancel,
        payload: Int = Keys.DIALOG_EMPTY_PAYLOAD_INT,
        payloadString: String = Keys.DIALOG_EMPTY_PAYLOAD_STRING
    ) {

        val builder = MaterialAlertDialogBuilder(context)

        builder.setMessage(messageString)
        if (title != Keys.EMPTY_STRING_RESOURCE) {
            builder.setTitle(context.getString(title))
        }


        builder.setPositiveButton(yesButton) { _, _ ->
            yesNoDialogListener.onYesNoDialog(type, true, payload, payloadString)
        }

        builder.setNegativeButton(noButton) { _, _ ->
            yesNoDialogListener.onYesNoDialog(type, false, payload, payloadString)
        }

        builder.setOnCancelListener {
            yesNoDialogListener.onYesNoDialog(type, false, payload, payloadString)
        }

        dialog = builder.create()
        dialog.show()
    }
}
