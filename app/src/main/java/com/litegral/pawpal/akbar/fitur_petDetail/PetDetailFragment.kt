package com.litegral.pawpal.akbar.fitur_petDetail

import android.graphics.Color
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageButton
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import androidx.navigation.fragment.navArgs
import androidx.viewpager2.widget.ViewPager2
import com.bumptech.glide.Glide
import com.google.android.material.tabs.TabLayout
import com.google.android.material.tabs.TabLayoutMediator
import com.google.firebase.firestore.FirebaseFirestore
import com.litegral.pawpal.R
import com.litegral.pawpal.akbar.model.CatModel
import com.litegral.pawpal.akbar.fitur_petDetail.adapter.PetImageSliderAdapter
import de.hdodenhof.circleimageview.CircleImageView

class PetDetailFragment : Fragment() {

    private val args: PetDetailFragmentArgs by navArgs()
    private lateinit var db: FirebaseFirestore

    // Deklarasi semua Views
    private lateinit var viewPager: ViewPager2
    private lateinit var tabLayout: TabLayout
    private lateinit var buttonBack: ImageButton
    private lateinit var textPetName: TextView
    private lateinit var textGender: TextView
    private lateinit var textPetBreed: TextView
    private lateinit var textPetAge: TextView
    private lateinit var textPetDescription: TextView
    private lateinit var buttonAdoptMe: Button
    private lateinit var progressBar: ProgressBar
    private lateinit var imageOwnerProfile: CircleImageView
    private lateinit var textOwnerName: TextView
    private lateinit var textOwnerLabel: TextView
    private lateinit var textViewCertified: TextView

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_pet_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        db = FirebaseFirestore.getInstance()
        initViews(view)
        setupClickListeners()
        loadPetAndOwnerDetails(args.petId)
    }

    private fun initViews(view: View) {
        viewPager = view.findViewById(R.id.viewPager_pet_images)
        tabLayout = view.findViewById(R.id.tabLayout_image_indicator)
        buttonBack = view.findViewById(R.id.button_back_detail)
        textPetName = view.findViewById(R.id.textView_pet_name_detail)
        textGender = view.findViewById(R.id.textView_gender_detail)
        textPetBreed = view.findViewById(R.id.textView_pet_breed_detail)
        textPetAge = view.findViewById(R.id.textView_pet_age_detail)
        textPetDescription = view.findViewById(R.id.textView_pet_description_detail)
        buttonAdoptMe = view.findViewById(R.id.button_adopt_me)
        progressBar = view.findViewById(R.id.progressBar_detail)
        imageOwnerProfile = view.findViewById(R.id.imageView_owner_profile_detail)
        textOwnerName = view.findViewById(R.id.textView_owner_name_detail)
        textOwnerLabel = view.findViewById(R.id.textView_owner_label_detail)
        textViewCertified = view.findViewById(R.id.textView_certified_detail)
    }

    private fun setupClickListeners() {
        buttonBack.setOnClickListener {
            findNavController().popBackStack()
        }
        buttonAdoptMe.setOnClickListener {
            try {
                // Navigasi dengan mengirim petId, pastikan action & argumen ada di nav_graph
                val action = PetDetailFragmentDirections.actionPetDetailFragmentToAdoptionFormFragment(args.petId)
                findNavController().navigate(action)
            } catch (e: Exception) {
                Log.e("PetDetailFragment", "Navigasi ke AdoptionFormFragment gagal.", e)
            }
        }
    }

    private fun loadPetAndOwnerDetails(petId: String) {
        setLoading(true)
        Log.d("PetDetail", "Mulai mengambil data hewan untuk ID: $petId")

        db.collection("pets").document(petId).get()
            .addOnSuccessListener { petDocument ->
                if (petDocument != null && petDocument.exists()) {
                    val pet = petDocument.toObject(CatModel::class.java)
                    if (pet != null) {
                        displayPetData(pet)
                        val uploaderUid = pet.uploaderUid
                        if (uploaderUid.isNotEmpty()) {
                            loadOwnerDetails(uploaderUid)
                        } else {
                            Log.w("PetDetail", "Uploader UID kosong, tidak bisa mengambil data pemilik.")
                            displayOwnerData("Pemilik", "") // Tampilkan nama default
                            setLoading(false)
                        }
                    } else {
                        handleDataNotFound()
                        setLoading(false)
                    }
                } else {
                    handleDataNotFound()
                    setLoading(false)
                }
            }
            .addOnFailureListener { exception ->
                setLoading(false)
                Log.e("PetDetail", "Gagal mengambil data HEWAN: ", exception)
            }
    }

    private fun loadOwnerDetails(uploaderId: String) {
        db.collection("users").document(uploaderId).get()
            .addOnSuccessListener { userDocument ->
                var ownerName = "Pemilik"
                var ownerPhotoUrl = ""
                if (userDocument != null && userDocument.exists()) {
                    ownerName = userDocument.getString("displayName") ?: "Pemilik"
                    ownerPhotoUrl = userDocument.getString("profilePhotoUrl") ?: ""
                    Log.d("PetDetail", "Data pemilik ditemukan: Nama=$ownerName, URL Foto=$ownerPhotoUrl")
                } else {
                    Log.w("PetDetail", "Dokumen pemilik TIDAK DITEMUKAN untuk UID: $uploaderId")
                }
                displayOwnerData(ownerName, ownerPhotoUrl)
                setLoading(false) // Sembunyikan loading setelah SEMUA proses selesai
            }
            .addOnFailureListener { exception ->
                setLoading(false) // Sembunyikan loading jika gagal
                Log.e("PetDetail", "Gagal mengambil data PEMILIK: ", exception)
                displayOwnerData("Pemilik", "")
            }
    }

    private fun displayPetData(pet: CatModel) {
        textPetName.text = pet.name
        textPetBreed.text = pet.breed
        textPetAge.text = pet.age
        textPetDescription.text = pet.description
        if (pet.isFemale) {
            textGender.text = "♀"; textGender.setTextColor(Color.parseColor("#F200FF"))
        } else {
            textGender.text = "♂"; textGender.setTextColor(Color.parseColor("#FCA93F"))
        }
        textGender.visibility = View.VISIBLE

        // Setup slider gambar dengan daftar URL dari Firestore
        Log.d("PetDetail", "Mencoba memuat gambar dari daftar URL: ${pet.imageUrls}")
        if (pet.imageUrls.isNotEmpty()) {
            val imageSliderAdapter = PetImageSliderAdapter(pet.imageUrls)
            viewPager.adapter = imageSliderAdapter

            if (pet.imageUrls.size > 1) {
                tabLayout.visibility = View.VISIBLE
                TabLayoutMediator(tabLayout, viewPager) { _, _ -> }.attach()
            } else {
                tabLayout.visibility = View.GONE
            }
        } else {
            Log.w("PetDetail", "Daftar imageUrls kosong.")
        }
    }

    private fun displayOwnerData(name: String, photoUrl: String) {
        textOwnerName.text = name
        textOwnerLabel.text = "Pemilik"
        Log.d("PetDetail", "Mencoba memuat FOTO PEMILIK dari URL: $photoUrl")
        if (photoUrl.isNotEmpty()) {
            Glide.with(this)
                .load(photoUrl)
                .placeholder(R.drawable.ic_profile_placeholder)
                .error(R.drawable.ic_profile_placeholder)
                .into(imageOwnerProfile)
        } else {
            Log.w("PetDetail", "URL foto pemilik kosong, menampilkan placeholder.")
            imageOwnerProfile.setImageResource(R.drawable.ic_profile_placeholder)
        }
    }

    private fun setLoading(isLoading: Boolean) {
        progressBar.isVisible = isLoading
    }

    private fun handleDataNotFound() {
        Toast.makeText(context, "Data hewan tidak ditemukan.", Toast.LENGTH_SHORT).show()
        textPetName.text = "Tidak Ditemukan"
    }
}