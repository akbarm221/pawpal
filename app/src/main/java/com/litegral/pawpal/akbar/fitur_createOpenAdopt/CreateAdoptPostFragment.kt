// Semua file berada di dalam package 'akbar'
package com.litegral.pawpal.akbar.fitur_createOpenAdopt

import android.net.Uri
import android.os.Bundle
import android.provider.OpenableColumns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.Spinner
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import com.bumptech.glide.Glide
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.storage.FirebaseStorage
import com.litegral.pawpal.R
import com.litegral.pawpal.akbar.model.CatModel
import java.util.UUID

class CreateAdoptPostFragment : Fragment() {

    // Menerima argumen petId (bisa null) dari fragment sebelumnya
    private val args: CreateAdoptPostFragmentArgs by navArgs()
    private var isEditMode = false
    private var petToEdit: CatModel? = null

    // Firebase instances
    private lateinit var auth: FirebaseAuth
    private lateinit var db: FirebaseFirestore
    private lateinit var storage: FirebaseStorage

    // Deklarasi View
    private lateinit var buttonBackForm: ImageButton
    private lateinit var textViewFormTitle: TextView
    private lateinit var editTextName: EditText
    private lateinit var editTextAge: EditText
    private lateinit var spinnerGender: Spinner
    private lateinit var editTextBreed: EditText
    private lateinit var editTextDescription: EditText
    private lateinit var buttonUploadDocument: Button
    private lateinit var layoutSelectedDocuments: LinearLayout
    private lateinit var imageViewProfilePhotoPreview: ImageView
    private lateinit var buttonSubmitAdoptPost: Button
    private lateinit var progressBar: ProgressBar

    // Variabel untuk menyimpan URI file yang dipilih
    private var selectedProfilePhotoUri: Uri? = null
    private var selectedDocumentUris: MutableList<Uri> = mutableListOf()
    private var existingImageUrls: MutableList<String> = mutableListOf()

    // Launcher untuk memilih foto profil (satu file)
    private val pickProfilePhotoLauncher =
        registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
            if (uri != null) {
                selectedProfilePhotoUri = uri
                imageViewProfilePhotoPreview.setImageURI(uri)
                imageViewProfilePhotoPreview.setPadding(0, 0, 0, 0)
            }
        }

    // Launcher untuk memilih dokumen (bisa beberapa file)
    private val pickDocumentsLauncher =
        registerForActivityResult(ActivityResultContracts.GetMultipleContents()) { uris ->
            if (uris.isNotEmpty()) {
                selectedDocumentUris.addAll(uris)
                updateSelectedDocumentsUI()
            }
        }


    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_create_adopt_post, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Inisialisasi Firebase
        auth = FirebaseAuth.getInstance()
        db = FirebaseFirestore.getInstance()
        storage = FirebaseStorage.getInstance()

        initViews(view)
        setupSpinner()
        setupClickListeners()

        // Memeriksa apakah fragment dibuka dalam mode edit atau create
        val petIdFromArgs = args.petId
        if (petIdFromArgs != null) {
            isEditMode = true
            loadPetDataForEdit(petIdFromArgs)
        } else {
            isEditMode = false
            setupCreateMode()
        }
    }

    private fun initViews(view: View) {
        buttonBackForm = view.findViewById(R.id.button_back_form)
        textViewFormTitle = view.findViewById(R.id.textView_form_title)
        editTextName = view.findViewById(R.id.editText_name_form_post)
        editTextAge = view.findViewById(R.id.editText_age_form_post)
        spinnerGender = view.findViewById(R.id.spinner_gender_form_post)
        editTextBreed = view.findViewById(R.id.editText_breed_form_post)
        editTextDescription = view.findViewById(R.id.editText_description_form_post)
        buttonUploadDocument = view.findViewById(R.id.button_upload_document)
        layoutSelectedDocuments = view.findViewById(R.id.layout_selected_documents)
        imageViewProfilePhotoPreview = view.findViewById(R.id.imageView_profile_photo_preview)
        buttonSubmitAdoptPost = view.findViewById(R.id.button_submit_adopt_post)
        progressBar = view.findViewById(R.id.progressBar_form)
    }

    private fun setupSpinner() {
        val genderArray = resources.getStringArray(R.array.gender_array)

        val adapter = object : ArrayAdapter<String>(
            requireContext(),
            android.R.layout.simple_spinner_item,
            genderArray
        ) {
            override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getView(position, convertView, parent) as TextView
                view.setTextColor(resources.getColor(android.R.color.black)) // Warna teks saat terpilih
                return view
            }

            override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
                val view = super.getDropDownView(position, convertView, parent) as TextView
                view.setTextColor(resources.getColor(android.R.color.black)) // Teks dropdown warna hitam
                view.setBackgroundColor(resources.getColor(android.R.color.white)) // Background dropdown warna putih
                return view
            }
        }

        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        spinnerGender.adapter = adapter
    }


    private fun setupClickListeners() {
        buttonBackForm.setOnClickListener { findNavController().popBackStack() }
        imageViewProfilePhotoPreview.setOnClickListener { pickProfilePhotoLauncher.launch("image/*") }
        buttonUploadDocument.setOnClickListener { pickDocumentsLauncher.launch("image/*") }
        buttonSubmitAdoptPost.setOnClickListener { submitForm() }
    }

    // Fungsi untuk mode EDIT: memuat data dari Firestore dan mengisi form
    private fun loadPetDataForEdit(petId: String) {
        setLoading(true)
        textViewFormTitle.text = "Update Post"
        buttonSubmitAdoptPost.text = "Save Changes"

        db.collection("pets").document(petId).get()
            .addOnSuccessListener { document ->
                if (document != null && document.exists()) {
                    petToEdit = document.toObject(CatModel::class.java)
                    if (petToEdit != null) {
                        // Isi form dengan data yang ada
                        editTextName.setText(petToEdit!!.name)
                        editTextAge.setText(petToEdit!!.age)
                        editTextBreed.setText(petToEdit!!.breed)
                        editTextDescription.setText(petToEdit!!.description)
                        spinnerGender.setSelection(if (petToEdit!!.isFemale) 1 else 0) // Asumsi Male=0, Female=1

                        existingImageUrls.addAll(petToEdit!!.imageUrls)
                        if (existingImageUrls.isNotEmpty()) {
                            Glide.with(this).load(existingImageUrls[0]).into(imageViewProfilePhotoPreview)
                            imageViewProfilePhotoPreview.setPadding(0, 0, 0, 0)
                        }
                        updateSelectedDocumentsUI()
                    }
                }
                setLoading(false)
            }
            .addOnFailureListener {
                setLoading(false)
                Toast.makeText(context, "Gagal memuat data untuk diedit.", Toast.LENGTH_SHORT).show()
            }
    }

    // Fungsi untuk mode CREATE: memastikan form kosong
    private fun setupCreateMode() {
        textViewFormTitle.text = "Open Adopt Form"
        buttonSubmitAdoptPost.text = "Upload Postingan"
    }

    // Fungsi utama yang dipanggil saat tombol submit ditekan
    private fun submitForm() {
        val name = editTextName.text.toString().trim()
        val age = editTextAge.text.toString().trim()
        val breed = editTextBreed.text.toString().trim()
        val description = editTextDescription.text.toString().trim()
        val isFemale = spinnerGender.selectedItem.toString().equals("Female", true)

        if (name.isBlank() || age.isBlank()) {
            Toast.makeText(context, "Nama dan Umur wajib diisi.", Toast.LENGTH_SHORT).show()
            return
        }
        setLoading(true)

        if (isEditMode) {
            updatePostInFirestore(args.petId!!, name, age, isFemale, breed, description)
        } else {
            if (selectedProfilePhotoUri == null) {
                Toast.makeText(context, "Foto Profil wajib diisi.", Toast.LENGTH_SHORT).show()
                setLoading(false)
                return
            }
            uploadAllImagesAndCreatePost(name, age, isFemale, breed, description)
        }
    }

    // Mengunggah semua gambar (profil+dokumen) lalu menyimpan data ke Firestore
    private fun uploadAllImagesAndCreatePost(name: String, age: String, isFemale: Boolean, breed: String, description: String) {
        val allUrisToUpload = mutableListOf<Uri>()
        selectedProfilePhotoUri?.let { allUrisToUpload.add(it) } // Foto profil di urutan pertama
        allUrisToUpload.addAll(selectedDocumentUris)

        val uploadedUrls = mutableListOf<String>()
        var uploadCounter = 0
        if (allUrisToUpload.isEmpty()) {
            savePostToFirestore(name, age, isFemale, breed, description, listOf())
            return
        }

        allUrisToUpload.forEach { uri ->
            val uploaderId = auth.currentUser?.uid ?: return@forEach
            val fileName = "${UUID.randomUUID()}.jpg"
            val imageRef = storage.reference.child("images/$uploaderId/$fileName")
            imageRef.putFile(uri)
                .addOnSuccessListener {
                    imageRef.downloadUrl.addOnSuccessListener { downloadUrl ->
                        // Simpan sementara URL yang didapat
                        if (uri == selectedProfilePhotoUri) {
                            uploadedUrls.add(0, downloadUrl.toString()) // Pastikan URL profil di indeks 0
                        } else {
                            uploadedUrls.add(downloadUrl.toString())
                        }

                        uploadCounter++
                        if (uploadCounter == allUrisToUpload.size) {
                            savePostToFirestore(name, age, isFemale, breed, description, uploadedUrls)
                        }
                    }
                }
                .addOnFailureListener { e ->
                    setLoading(false); Toast.makeText(context, "Gagal unggah gambar: ${e.message}", Toast.LENGTH_SHORT).show()
                }
        }
    }

    // Menyimpan dokumen postingan baru ke Firestore
    private fun savePostToFirestore(name: String, age: String, isFemale: Boolean, breed: String, description: String, imageUrls: List<String>) {
        val uploaderId = auth.currentUser?.uid ?: return
        val petId = db.collection("pets").document().id

        val newPet = CatModel(
            id = petId, uploaderUid = uploaderId,
            uploaderName = auth.currentUser?.displayName ?: "Pengguna",
            name = name, age = age, isFemale = isFemale,
            breed = breed, description = description, imageUrls = imageUrls,
            petPosition = "Bogor", // TODO: Sesuaikan
            postedDate = null // Akan diisi oleh server
        )

        db.collection("pets").document(petId).set(newPet)
            .addOnSuccessListener {
                Toast.makeText(context, "Postingan berhasil diunggah!", Toast.LENGTH_LONG).show()
                parentFragmentManager.setFragmentResult("newPetPostRequest", Bundle().apply { putParcelable("newlyPostedPet", newPet) })
                findNavController().popBackStack()
            }
            .addOnFailureListener { e -> Toast.makeText(context, "Gagal menyimpan postingan: ${e.message}", Toast.LENGTH_LONG).show() }
            .addOnCompleteListener { setLoading(false) }
    }

    // Mengupdate dokumen yang ada di Firestore (hanya data teks untuk saat ini)
    private fun updatePostInFirestore(petId: String, name: String, age: String, isFemale: Boolean, breed: String, description: String) {
        // TODO: Implementasi logika upload untuk gambar baru dan hapus gambar lama jika ada perubahan
        val updatedData = mapOf(
            "name" to name, "age" to age, "isFemale" to isFemale,
            "breed" to breed, "description" to description
        )
        db.collection("pets").document(petId).update(updatedData)
            .addOnSuccessListener {
                Toast.makeText(context, "Postingan berhasil diperbarui!", Toast.LENGTH_SHORT).show()
                // Kembali ke halaman riwayat
                findNavController().popBackStack(R.id.postingOpenAdoptHistoryFragment, false)
            }
            .addOnFailureListener { e -> Toast.makeText(context, "Gagal memperbarui: ${e.message}", Toast.LENGTH_SHORT).show() }
            .addOnCompleteListener { setLoading(false) }
    }

    private fun setLoading(isLoading: Boolean) {
        progressBar.isVisible = isLoading
        buttonSubmitAdoptPost.isEnabled = !isLoading
    }

    // Fungsi-fungsi helper untuk UI dokumen
    private fun updateSelectedDocumentsUI() {
        layoutSelectedDocuments.removeAllViews()
        existingImageUrls.drop(1).forEach { url ->
            val fileName = url.substringAfterLast('%', "Dokumen").substringAfter('F').substringBefore('?').take(15) + "..."
            layoutSelectedDocuments.addView(createDocumentTextView(fileName))
        }
        selectedDocumentUris.forEach { uri ->
            layoutSelectedDocuments.addView(createDocumentTextView(getFileName(uri) ?: "Dokumen Baru"))
        }
    }
    private fun createDocumentTextView(fileName: String): TextView {
        return TextView(context).apply {
            text = fileName; textSize = 14f; setPadding(0, 8, 0, 8)
            setCompoundDrawablesWithIntrinsicBounds(R.drawable.ic_document_upload, 0, 0, 0)
            compoundDrawablePadding = 16
        }
    }
    private fun getFileName(uri: Uri): String? {
        context?.contentResolver?.query(uri, null, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (nameIndex != -1) return cursor.getString(nameIndex)
            }
        }
        return null
    }
}