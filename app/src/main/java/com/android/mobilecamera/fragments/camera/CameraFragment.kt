package com.android.mobilecamera.fragments.camera

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import com.android.mobilecamera.R
import com.android.mobilecamera.databinding.FragmentCameraBinding
import com.google.android.material.dialog.MaterialAlertDialogBuilder

class CameraFragment : Fragment() {

    private var _binding: FragmentCameraBinding? = null
    private val binding get() = _binding!!

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        val allGranted = permissions.entries.all { it.value }

        if (allGranted) {
            startCamera()
        } else {
            Toast.makeText(requireContext(), "Без прав камера не работает :(", Toast.LENGTH_LONG).show()
            binding.placeholderText.text = "Нет прав доступа.\nПерейдите в настройки."
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCameraBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        if (requireContext().hasCameraAccess()) {
            startCamera()
        } else {
            checkPermissionsWithRationale()
        }

        binding.galleryBtn.setOnClickListener {
            findNavController().navigate(R.id.action_cameraFragment_to_galleryFragment)
        }
    }

    private fun checkPermissionsWithRationale() {
        val shouldShowRationale = requiredCameraPermissions.any { permission ->
            shouldShowRequestPermissionRationale(permission)
        }

        if (shouldShowRationale) {
            MaterialAlertDialogBuilder(requireContext())
                .setTitle("Нужен доступ")
                .setMessage("Приложению нужна камера для съемки и хранилище для сохранения фото. Пожалуйста, разрешите доступ.")
                .setPositiveButton("ОК") { _, _ ->
                    permissionLauncher.launch(requiredCameraPermissions)
                }
                .setNegativeButton("Нет") { dialog, _ ->
                    dialog.dismiss()
                    binding.placeholderText.text = "Доступ запрещен пользователем"
                }
                .show()
        } else {
            permissionLauncher.launch(requiredCameraPermissions)
        }
    }

    private fun startCamera() {
        binding.placeholderText.text = "Камера готова к запуску!\n(Preview)"
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}