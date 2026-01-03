package com.android.mobilecamera.fragments.gallery

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.marginBottom
import androidx.core.view.updateLayoutParams
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.GridLayoutManager
import com.android.mobilecamera.databinding.FragmentGalleryBinding
import kotlinx.coroutines.launch

class GalleryFragment : Fragment() {

    private var _binding: FragmentGalleryBinding? = null
    private val binding get() = _binding!!

    private val viewModel: GalleryViewModel by viewModels {
        GalleryViewModelFactory(requireActivity().application)
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentGalleryBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        val adapter = GalleryAdapter()
        binding.recyclerView.apply {
            this.adapter = adapter
            layoutManager = GridLayoutManager(requireContext(), 3)
        }

        val fabMarginBottom = binding.mockBtn.marginBottom
        val clearFabMarginBottom = binding.clearBtn.marginBottom

        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { view, insets ->
            val statusBars = insets.getInsets(WindowInsetsCompat.Type.statusBars())
            val navBars = insets.getInsets(WindowInsetsCompat.Type.navigationBars())
            val systemGestures = insets.getInsets(WindowInsetsCompat.Type.systemGestures())

            binding.recyclerView.updatePadding(
                top = statusBars.top,
                bottom = navBars.bottom + fabMarginBottom + binding.mockBtn.height,
                left = systemGestures.left,
                right = systemGestures.right
            )

            binding.mockBtn.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = fabMarginBottom + navBars.bottom
            }
            binding.clearBtn.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                bottomMargin = clearFabMarginBottom + navBars.bottom
            }

            binding.emptyText.updatePadding(
                top = statusBars.top,
                bottom = navBars.bottom,
                left = systemGestures.left,
                right = systemGestures.right
            )

            insets
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                viewModel.mediaList.collect { mediaList ->
                    adapter.submitList(mediaList)
                    binding.emptyText.visibility = if (mediaList.isEmpty()) View.VISIBLE else View.GONE
                }
            }
        }

        binding.mockBtn.setOnClickListener {
            viewModel.createMocks()
        }

        binding.clearBtn.setOnClickListener {
            viewModel.clearAll()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}