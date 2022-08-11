/*
 * Copyright (C) 2022 The LineageOS Project
 *
 * SPDX-License-Identifier: Apache-2.0
 */

package org.lineageos.aperture

import android.app.Activity
import android.app.KeyguardManager
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Intent
import android.text.SpannableString
import android.text.method.LinkMovementMethod
import android.text.util.Linkify
import android.view.View
import android.widget.AdapterView
import android.widget.ImageButton
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.core.view.isVisible
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.Result
import com.google.zxing.common.HybridBinarizer
import java.lang.Exception

class QrImageAnalyzer(private val activity: Activity) : ImageAnalysis.Analyzer {
    private val bottomSheetDialog by lazy {
        BottomSheetDialog(activity).apply {
            setContentView(R.layout.qr_bottom_sheet_dialog)
        }
    }
    private val bottomSheetDialogBytes by lazy { bottomSheetDialog.findViewById<TextView>(R.id.bytes) }
    private val bottomSheetDialogBytesTitle by lazy { bottomSheetDialog.findViewById<TextView>(R.id.bytes_title) }
    private val bottomSheetDialogText by lazy { bottomSheetDialog.findViewById<TextView>(R.id.text) }
    private val bottomSheetDialogTextTitle by lazy { bottomSheetDialog.findViewById<TextView>(R.id.text_title) }
    private val bottomSheetDialogType by lazy { bottomSheetDialog.findViewById<TextView>(R.id.type) }
    private val bottomSheetDialogSetType by lazy { bottomSheetDialog.findViewById<Spinner>(R.id.set_type) }

    private val reader by lazy { MultiFormatReader() }

    private val clipboardManager by lazy { activity.getSystemService(ClipboardManager::class.java) }
    private val keyguardManager by lazy { activity.getSystemService(KeyguardManager::class.java) }

    enum class Type(val value: Int) {
        BYTES(0),
        TEXT(1),
    }

    private var type: Type = Type.TEXT
        set(value) {
            field = value
            bottomSheetDialogBytes?.isVisible = type == Type.BYTES
            bottomSheetDialogBytesTitle?.isVisible = type == Type.BYTES
            bottomSheetDialogText?.isVisible = type == Type.TEXT
            bottomSheetDialogTextTitle?.isVisible = type == Type.TEXT
        }

    override fun analyze(image: ImageProxy) {
        val binaryBitmap = BinaryBitmap(HybridBinarizer(image.planarYUVLuminanceSource))

        runCatching {
            showQrDialog(reader.decodeWithState(binaryBitmap))
        }

        image.close()
    }

    private fun showQrDialog(result: Result) {
        activity.runOnUiThread {
            if (bottomSheetDialog.isShowing) {
                return@runOnUiThread
            }

            // Set barcode type
            bottomSheetDialogType?.text = result.barcodeFormat.name

            // Set bytes
            bottomSheetDialogBytes?.text =
                result.rawBytes?.joinToString(" ") { "%02X".format(it) }

            // Set type to text by default
            type = Type.TEXT

            // Linkify message
            SpannableString(result.text).let {
                Linkify.addLinks(it, Linkify.ALL)
                bottomSheetDialogText?.text = it
                bottomSheetDialogText?.movementMethod =
                    if (!keyguardManager.isKeyguardLocked) LinkMovementMethod.getInstance()
                    else null
            }

            // Set type spinner
            bottomSheetDialog.findViewById<Spinner>(R.id.set_type)?.onItemSelectedListener =
                object : AdapterView.OnItemSelectedListener {
                    override fun onNothingSelected(parent: AdapterView<*>?) {}

                    override fun onItemSelected(
                        parent: AdapterView<*>?,
                        view: View?,
                        position: Int,
                        id: Long
                    ) {
                        type = when (position) {
                            0 -> Type.BYTES
                            1 -> Type.TEXT
                            else -> throw Exception("Unknown type spinner position")
                        }
                    }
                }
            bottomSheetDialogSetType?.setSelection(Type.TEXT.ordinal)
            bottomSheetDialogSetType?.isVisible = !bottomSheetDialogBytes?.text.isNullOrEmpty()

            // Set buttons
            bottomSheetDialog.findViewById<ImageButton>(R.id.copy)?.setOnClickListener {
                clipboardManager.setPrimaryClip(
                    ClipData.newPlainText(
                        "", when (type) {
                            Type.TEXT -> result.text
                            Type.BYTES -> bottomSheetDialogBytes?.text
                        }
                    )
                )
                Toast.makeText(
                    activity, when (type) {
                        Type.BYTES -> activity.getString(R.string.qr_copy_toast_bytes)
                        Type.TEXT -> activity.getString(R.string.qr_copy_toast_text)
                    }, Toast.LENGTH_SHORT
                ).show()
            }

            bottomSheetDialog.findViewById<ImageButton>(R.id.share)?.setOnClickListener {
                activity.startActivity(
                    Intent.createChooser(
                        Intent().apply {
                            action = Intent.ACTION_SEND
                            type = "text/plain"
                            putExtra(
                                Intent.EXTRA_TEXT, when (this@QrImageAnalyzer.type) {
                                    Type.TEXT -> result.text
                                    Type.BYTES -> bottomSheetDialogBytes?.text
                                }
                            )
                        },
                        activity.getString(androidx.transition.R.string.abc_shareactionprovider_share_with)
                    )
                )
            }

            // Show dialog
            bottomSheetDialog.show()
        }
    }
}