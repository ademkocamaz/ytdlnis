package com.deniscerri.ytdlnis.ui.downloadcard

import android.annotation.SuppressLint
import android.app.Dialog
import android.content.*
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.LayoutInflater
import android.view.View
import android.widget.*
import androidx.core.view.forEach
import androidx.core.view.isVisible
import androidx.lifecycle.lifecycleScope
import androidx.preference.PreferenceManager
import com.deniscerri.ytdlnis.R
import com.deniscerri.ytdlnis.database.models.DownloadItem
import com.deniscerri.ytdlnis.database.models.Format
import com.deniscerri.ytdlnis.database.viewmodel.DownloadViewModel.Type
import com.deniscerri.ytdlnis.util.FileUtil
import com.deniscerri.ytdlnis.util.InfoUtil
import com.deniscerri.ytdlnis.util.UiUtil
import com.facebook.shimmer.ShimmerFrameLayout
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import com.google.android.material.card.MaterialCardView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.*


class FormatSelectionBottomSheetDialog(private val items: List<DownloadItem?>, private var formats: List<List<Format>>, private val listener: OnFormatClickListener) : BottomSheetDialogFragment() {
    private lateinit var behavior: BottomSheetBehavior<View>
    private lateinit var infoUtil: InfoUtil
    private lateinit var sharedPreferences: SharedPreferences
    private lateinit var formatCollection: MutableList<List<Format>>
    private lateinit var chosenFormats: List<Format>
    private lateinit var selectedVideo : Format
    private lateinit var selectedAudios : MutableList<Format>

    private lateinit var videoFormatList : LinearLayout
    private lateinit var audioFormatList : LinearLayout
    private lateinit var okBtn : Button
    private lateinit var videoTitle : TextView
    private lateinit var audioTitle : TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        infoUtil = InfoUtil(requireActivity().applicationContext)
        formatCollection = mutableListOf()
        chosenFormats = listOf()
        selectedAudios = mutableListOf()
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext())
    }


    @SuppressLint("RestrictedApi")
    override fun setupDialog(dialog: Dialog, style: Int) {
        super.setupDialog(dialog, style)
        val view = LayoutInflater.from(context).inflate(R.layout.format_select_bottom_sheet, null)
        dialog.setContentView(view)

        dialog.setOnShowListener {
            behavior = BottomSheetBehavior.from(view.parent as View)
            val displayMetrics = DisplayMetrics()
            requireActivity().windowManager.defaultDisplay.getMetrics(displayMetrics)
            behavior.peekHeight = displayMetrics.heightPixels / 2
        }

        val formatListLinearLayout = view.findViewById<LinearLayout>(R.id.format_list_linear_layout)
        val shimmers = view.findViewById<ShimmerFrameLayout>(R.id.format_list_shimmer)

        videoFormatList = view.findViewById(R.id.video_linear_layout)
        audioFormatList = view.findViewById(R.id.audio_linear_layout)
        videoTitle = view.findViewById(R.id.video_title)
        audioTitle = view.findViewById(R.id.audio_title)
        okBtn = view.findViewById(R.id.format_ok)

        shimmers.visibility = View.GONE
        val hasGenericFormats =  when(items.first()!!.type){
            Type.audio -> formats.first().size == resources.getStringArray(R.array.audio_formats).size
            else -> formats.first().size == resources.getStringArray(R.array.video_formats).size
        }
        if (items.size > 1){

            if (!hasGenericFormats){
                formatCollection.addAll(formats)
                val flattenFormats = formats.flatten()
                val commonFormats = flattenFormats.groupingBy { it.format_id }.eachCount().filter { it.value == items.size }.mapValues { flattenFormats.first { f -> f.format_id == it.key } }.map { it.value }
                chosenFormats = commonFormats.mapTo(mutableListOf()) {it.copy()}
                chosenFormats = when(items.first()?.type){
                    Type.audio -> chosenFormats.filter { it.format_note.contains("audio", ignoreCase = true) }
                    else -> chosenFormats.filter { !it.format_note.contains("audio", ignoreCase = true) }
                }
                chosenFormats.forEach {
                    it.filesize =
                        flattenFormats.filter { f -> f.format_id == it.format_id }
                            .sumOf { itt -> itt.filesize }
                }
            }else{
                chosenFormats = formats.flatten()
            }
            addFormatsToView()
        }else{
            chosenFormats = formats.flatten()
            if(!hasGenericFormats){
                if(items.first()?.type == Type.audio){
                    chosenFormats =  chosenFormats.filter { it.format_note.contains("audio", ignoreCase = true) }
                }
            }
            addFormatsToView()
        }

        val refreshBtn = view.findViewById<Button>(R.id.format_refresh)
        if (!hasGenericFormats || items.isEmpty()) refreshBtn.visibility = View.GONE


        refreshBtn.setOnClickListener {
           lifecycleScope.launch {
               chosenFormats = emptyList()
               try {
                   refreshBtn.isEnabled = false
                   formatListLinearLayout.visibility = View.GONE
                   shimmers.visibility = View.VISIBLE
                   shimmers.startShimmer()

                   //simple download
                   if (items.size == 1){
                       val res = withContext(Dispatchers.IO){
                           infoUtil.getFormats(items.first()!!.url)
                       }
                       res.filter { it.format_note != "storyboard" }
                       if(items.first()?.type == Type.audio){
                           chosenFormats = res.filter { it.format_note.contains("audio", ignoreCase = true) }
                       }else{
                           chosenFormats = res
                       }
                       if (chosenFormats.isEmpty()) throw Exception()
                   //playlist format filtering
                   }else{
                       var progress = "0/${items.size}"
                       formatCollection.clear()
                       refreshBtn.text = progress
                       withContext(Dispatchers.IO){
                           infoUtil.getFormatsMultiple(items.map { it!!.url }) {
                               lifecycleScope.launch(Dispatchers.Main){
                                   progress = "${formatCollection.size}/${items.size}"
                                   refreshBtn.text = progress
                               }
                               formatCollection.add(it)
                           }
                       }
                       val flatFormatCollection = formatCollection.flatten()
                       val commonFormats = flatFormatCollection.groupingBy { it.format_id }.eachCount().filter { it.value == items.size }.mapValues { flatFormatCollection.first { f -> f.format_id == it.key } }.map { it.value }
                       chosenFormats = commonFormats.filter { it.filesize != 0L }.mapTo(mutableListOf()) {it.copy()}
                       chosenFormats = when(items.first()?.type){
                           Type.audio -> chosenFormats.filter { it.format_note.contains("audio", ignoreCase = true) }
                           else -> chosenFormats.filter { !it.format_note.contains("audio", ignoreCase = true) }
                       }
                       if (chosenFormats.isEmpty()) throw Exception()
                       chosenFormats.forEach {
                           it.filesize =
                               flatFormatCollection.filter { f -> f.format_id == it.format_id }
                                   .sumOf { itt -> itt.filesize }
                       }
                   }
                   shimmers.visibility = View.GONE
                   shimmers.stopShimmer()
                   addFormatsToView()
                   refreshBtn.visibility = View.GONE
                   formatListLinearLayout.visibility = View.VISIBLE
               }catch (e: Exception){
                   runCatching {
                       refreshBtn.isEnabled = true
                       refreshBtn.text = getString(R.string.update_formats)
                       formatListLinearLayout.visibility = View.VISIBLE
                       shimmers.visibility = View.GONE
                       shimmers.stopShimmer()

                       e.printStackTrace()
                       Toast.makeText(context, getString(R.string.error_updating_formats), Toast.LENGTH_SHORT).show()
                   }
               }
           }
        }

        okBtn.setOnClickListener {
            val selectedFormats = mutableListOf<Format>()
            if (!::selectedVideo.isInitialized) {
                selectedVideo =
                    chosenFormats.filter { it.vcodec.isNotBlank() && it.vcodec != "none" }.maxByOrNull { it.filesize }!!
            }
            selectedFormats.add(selectedVideo)
            selectedFormats.addAll(selectedAudios)
            listener.onFormatClick(List(items.size){chosenFormats}, selectedFormats)
            dismiss()
        }

        if (sharedPreferences.getBoolean("update_formats", false) && refreshBtn.isVisible && items.size == 1){
            refreshBtn.performClick()
        }
    }
    private fun addFormatsToView(){
        val canMultiSelectAudio = items.first()?.type == Type.video && items.count() == 1 && chosenFormats.find { it.format_note.contains("audio", ignoreCase = true) } != null
        videoFormatList.removeAllViews()
        audioFormatList.removeAllViews()

        if (!canMultiSelectAudio) {
            audioFormatList.visibility = View.GONE
            videoTitle.visibility = View.GONE
            audioTitle.visibility = View.GONE
            okBtn.visibility = View.GONE
        }else{
            if (chosenFormats.count { it.vcodec.isBlank() || it.vcodec == "none" } == 0){
                audioFormatList.visibility = View.GONE
                audioTitle.visibility = View.GONE
                videoTitle.visibility = View.GONE
                okBtn.visibility = View.GONE
            }else{
                audioFormatList.visibility = View.VISIBLE
                audioTitle.visibility = View.VISIBLE
                videoTitle.visibility = View.VISIBLE
                okBtn.visibility = View.VISIBLE
            }
        }

        for (i in chosenFormats.lastIndex downTo 0){
            val format = chosenFormats[i]
            val formatItem = LayoutInflater.from(context).inflate(R.layout.format_item, null)
            formatItem.tag = "${format.format_id}${format.format_note}"
            UiUtil.populateFormatCard(formatItem as MaterialCardView, format, null)
            formatItem.setOnClickListener{ clickedformat ->
                //if the context is behind a single download card and its a video, allow the ability to multiselect audio formats
                if (canMultiSelectAudio){
                    val clickedCard = (clickedformat as MaterialCardView)
                    if (format.vcodec.isNotBlank() && format.vcodec != "none") {
                        if (clickedCard.isChecked) {
                            listener.onFormatClick(List(items.size){chosenFormats}, listOf(format))
                            dismiss()
                        }
                        videoFormatList.forEach { (it as MaterialCardView).isChecked = false }
                        selectedVideo = format
                        clickedCard.isChecked = true
                    }else{
                        if(selectedAudios.contains(format)) {
                            selectedAudios.remove(format)
                        } else {
                            selectedAudios.add(format)
                        }
                    }
                    audioFormatList.forEach { (it as MaterialCardView).isChecked = false }
                    audioFormatList.forEach {
                        (it as MaterialCardView).isChecked = selectedAudios.map { a -> "${a.format_id}${a.format_note}" }.contains(it.tag)
                    }
                }else{
                    if (items.size == 1){
                        listener.onFormatClick(List(items.size){chosenFormats}, listOf(format))
                    }else{
                        val selectedFormats = mutableListOf<Format>()
                        formatCollection.forEach {
                            selectedFormats.add(it.first{ f -> f.format_id == format.format_id})
                        }
                        if (selectedFormats.isEmpty()) {
                            items.forEach {
                                selectedFormats.add(format)
                            }
                        }
                        listener.onFormatClick(formatCollection, selectedFormats)
                    }
                    dismiss()
                }
            }
            formatItem.setOnLongClickListener {
                UiUtil.showFormatDetails(format, requireActivity())
                true
            }

            if (canMultiSelectAudio){
                if (format.vcodec.isNotBlank() && format.vcodec != "none") videoFormatList.addView(formatItem)
                else audioFormatList.addView(formatItem)
            }else{
                videoFormatList.addView(formatItem)
            }
        }
    }

    override fun onCancel(dialog: DialogInterface) {
        super.onCancel(dialog)
        cleanUp()
    }

    override fun onDismiss(dialog: DialogInterface) {
        super.onDismiss(dialog)
        cleanUp()
    }


    private fun cleanUp(){
        kotlin.runCatching {
            parentFragmentManager.beginTransaction().remove(parentFragmentManager.findFragmentByTag("formatSheet")!!).commit()
        }
    }
}

interface OnFormatClickListener{
    fun onFormatClick(allFormats: List<List<Format>>, item: List<Format>)
}