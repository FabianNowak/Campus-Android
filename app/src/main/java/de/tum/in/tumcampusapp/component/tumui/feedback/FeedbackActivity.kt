package de.tum.`in`.tumcampusapp.component.tumui.feedback

import android.annotation.SuppressLint
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager.PERMISSION_GRANTED
import android.location.Location
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.Toast
import android.widget.Toast.LENGTH_SHORT
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager.HORIZONTAL
import com.google.android.gms.location.LocationRequest
import com.jakewharton.rxbinding3.widget.checkedChanges
import com.jakewharton.rxbinding3.widget.textChanges
import com.patloew.rxlocation.RxLocation
import de.tum.`in`.tumcampusapp.R
import de.tum.`in`.tumcampusapp.component.other.generic.activity.BaseActivity
import de.tum.`in`.tumcampusapp.component.tumui.feedback.FeedbackPresenter.Companion.PERMISSION_CAMERA
import de.tum.`in`.tumcampusapp.component.tumui.feedback.FeedbackPresenter.Companion.PERMISSION_FILES
import de.tum.`in`.tumcampusapp.component.tumui.feedback.FeedbackPresenter.Companion.PERMISSION_LOCATION
import de.tum.`in`.tumcampusapp.databinding.ActivityFeedbackBinding
import de.tum.`in`.tumcampusapp.utils.Const
import de.tum.`in`.tumcampusapp.utils.ThemedAlertDialogBuilder
import de.tum.`in`.tumcampusapp.utils.Utils
import io.reactivex.Observable
import java.io.File
import javax.inject.Inject

class FeedbackActivity : BaseActivity(R.layout.activity_feedback), FeedbackContract.View {

    private lateinit var thumbnailsAdapter: FeedbackThumbnailsAdapter
    private var progressDialog: AlertDialog? = null

    @Inject
    lateinit var presenter: FeedbackContract.Presenter

    private lateinit var binding: ActivityFeedbackBinding

    private val cameraLauncher = registerForActivityResult(StartActivityForResult()) {
        presenter.onNewImageTaken()
    }

    private val galleryLauncher = registerForActivityResult(StartActivityForResult()) { result ->
        val filePath = result.data?.data
        presenter.onNewImageSelected(filePath)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityFeedbackBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val lrzId = Utils.getSetting(this, Const.LRZ_ID, "")
        injector.feedbackComponent()
                .lrzId(lrzId)
                .build()
                .inject(this)

        presenter.attachView(this)

        if (savedInstanceState != null) {
            presenter.onRestoreInstanceState(savedInstanceState)
        }

        initIncludeLocation()
        initPictureGallery()

        if (savedInstanceState == null) {
            presenter.initEmail()
        }
        initIncludeEmail()
    }

    public override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        presenter.onSaveInstanceState(outState)
    }

    private fun initPictureGallery() {
        binding.imageRecyclerView.layoutManager = LinearLayoutManager(this, HORIZONTAL, false)

        val imagePaths = presenter.feedback.picturePaths
        val thumbnailSize = resources.getDimension(R.dimen.thumbnail_size).toInt()
        thumbnailsAdapter = FeedbackThumbnailsAdapter(imagePaths, { onThumbnailRemoved(it) }, thumbnailSize)
        binding.imageRecyclerView.adapter = thumbnailsAdapter

        binding.addImageButton.setOnClickListener { showImageOptionsDialog() }
    }

    private fun onThumbnailRemoved(path: String) {
        val view = View.inflate(this, R.layout.picture_dialog, null)

        val imageView = view.findViewById<ImageView>(R.id.feedback_big_image)
        imageView.setImageURI(Uri.fromFile(File(path)))

        ThemedAlertDialogBuilder(this)
            .setView(view)
            .setNegativeButton(R.string.cancel, null)
            .setPositiveButton(R.string.feedback_remove_image) { _, _ -> removeThumbnail(path) }
            .show()
    }

    private fun removeThumbnail(path: String) {
        presenter.removeImage(path)
    }

    private fun showImageOptionsDialog() {
        val options = arrayOf(getString(R.string.feedback_take_picture), getString(R.string.gallery))
        ThemedAlertDialogBuilder(this)
                .setTitle(R.string.feedback_add_picture)
                .setItems(options) { _, index -> presenter.onImageOptionSelected(index) }
                .setNegativeButton(R.string.cancel, null)
                .show()
    }

    override fun getMessage(): Observable<String> =
            binding.feedbackMessage.textChanges().map { it.toString() }

    override fun getEmail(): Observable<String> =
            binding.customEmailInput.textChanges().map { it.toString() }

    override fun getTopicInput(): Observable<Int> = binding.radioButtonsGroup.checkedChanges()
    override fun getIncludeEmail(): Observable<Boolean> = binding.includeEmailCheckbox.checkedChanges()
    override fun getIncludeLocation(): Observable<Boolean> = binding.includeLocationCheckBox.checkedChanges()

    @SuppressLint("MissingPermission")
    override fun getLocation(): Observable<Location> = RxLocation(this).location().updates(LocationRequest.create())

    override fun setFeedback(message: String) {
        binding.feedbackMessage.setText(message)
    }

    override fun openCamera(intent: Intent) {
        try {
            cameraLauncher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.error_unknown, LENGTH_SHORT).show()
        }
    }

    override fun openGallery(intent: Intent) {
        try {
            galleryLauncher.launch(intent)
        } catch (e: ActivityNotFoundException) {
            Toast.makeText(this, R.string.error_unknown, LENGTH_SHORT).show()
        }
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    override fun showPermissionRequestDialog(permission: String, requestCode: Int) {
        requestPermissions(arrayOf(permission), requestCode)
    }

    private fun initIncludeLocation() {
        binding.includeLocationCheckBox.isChecked = presenter.feedback.includeLocation
    }

    private fun initIncludeEmail() {
        val feedback = presenter.feedback
        val email = feedback.email
        with(binding) {
            includeEmailCheckbox.isChecked = feedback.includeEmail

            if (presenter.lrzId.isEmpty()) {
                includeEmailCheckbox.text = getString(R.string.feedback_include_email)
                customEmailInput.setText(email)
            } else {
                includeEmailCheckbox.text = getString(R.string.feedback_include_email_tum_id, email)
            }
        }
    }

    override fun showEmailInput(show: Boolean) {
        binding.customEmailLayout.isVisible = show
    }

    fun onSendClicked(view: View) {
        presenter.onSendFeedback()
    }

    override fun showEmptyMessageError() {
        binding.feedbackMessage.error = getString(R.string.feedback_empty)
    }

    override fun showWarning(message: String) {
        Toast.makeText(this, message, Toast.LENGTH_LONG).show()
    }

    override fun showDialog(title: String, message: String) {
        ThemedAlertDialogBuilder(this)
                .setTitle(title)
                .setMessage(message)
                .setPositiveButton(R.string.ok, null)
                .show()
    }

    override fun showProgressDialog() {
        progressDialog = ThemedAlertDialogBuilder(this)
                .setTitle(R.string.feedback_sending)
                .setView(ProgressBar(this))
                .setCancelable(false)
                .setNeutralButton(R.string.cancel, null)
                .show()
    }

    override fun showSendErrorDialog() {
        progressDialog?.dismiss()

        ThemedAlertDialogBuilder(this)
                .setMessage(R.string.feedback_sending_error)
                .setIcon(R.drawable.ic_error_outline)
                .setPositiveButton(R.string.try_again) { _, _ -> presenter.feedback }
                .setNegativeButton(R.string.cancel, null)
                .show()
    }

    override fun onFeedbackSent() {
        progressDialog?.dismiss()
        Toast.makeText(this, R.string.feedback_send_success, LENGTH_SHORT).show()
        finish()
    }

    override fun showSendConfirmationDialog() {
        ThemedAlertDialogBuilder(this)
                .setMessage(R.string.send_feedback_question)
                .setPositiveButton(R.string.send) { _, _ -> presenter.onConfirmSend() }
                .setNegativeButton(R.string.cancel, null)
                .show()
    }

    override fun onImageAdded(path: String) {
        thumbnailsAdapter.addImage(path)
    }

    override fun onImageRemoved(position: Int) {
        thumbnailsAdapter.removeImage(position)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        if (grantResults.isEmpty()) {
            return
        }

        val isGranted = grantResults[0] == PERMISSION_GRANTED

        when (requestCode) {
            PERMISSION_LOCATION -> {
                binding.includeLocationCheckBox.isChecked = isGranted
                if (isGranted) {
                    presenter.listenForLocation()
                }
            }
            PERMISSION_CAMERA -> {
                if (isGranted) {
                    presenter.takePicture()
                }
            }
            PERMISSION_FILES -> {
                if (isGranted) {
                    presenter.openGallery()
                }
            }
        }
    }

    override fun onDestroy() {
        presenter.detachView()
        super.onDestroy()
    }
}
