package com.mamvid.kiosko.presentation.main

import android.app.AlertDialog
import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.WindowManager
import androidx.fragment.app.DialogFragment
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mamvid.kiosko.core.domain.model.AppSettings
import com.mamvid.kiosko.databinding.DialogAdminAuthBinding

class AdminAuthDialog : DialogFragment() {

    var currentSettings: AppSettings = AppSettings()
    var onAuthenticated: (() -> Unit)? = null

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val binding = DialogAdminAuthBinding.inflate(LayoutInflater.from(requireContext()))

        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle("Acceso Administrador")
            .setView(binding.root)
            .setPositiveButton("Entrar", null)
            .setNegativeButton("Cancelar") { d, _ -> d.dismiss() }
            .create()

        dialog.setOnShowListener {
            try {
                binding.etPassword.requestFocus()
                dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_STATE_VISIBLE)

                dialog.getButton(AlertDialog.BUTTON_POSITIVE)?.setOnClickListener {
                    val entered = binding.etPassword.text?.toString() ?: ""
                    if (entered == currentSettings.adminPassword) {
                        dismiss()
                        onAuthenticated?.invoke()
                    } else {
                        binding.tilPassword.error = "Contraseña incorrecta"
                        binding.etPassword.text?.clear()
                    }
                }
            } catch (e: Exception) {
                dismiss()
            }
        }

        return dialog
    }
}
