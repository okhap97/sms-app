package com.example.smsapp

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.telephony.SmsManager
import android.text.InputType
import android.view.Gravity
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions

class MainActivity : Activity() {

    private lateinit var status: TextView
    private lateinit var phoneInput: EditText
    private lateinit var messageInput: EditText
    private lateinit var autoCheck: CheckBox

    private val prefs by lazy { getSharedPreferences("sms", MODE_PRIVATE) }
    private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)
    private val phoneRegex = Regex("01[016789][-.\\s]?\\d{3,4}[-.\\s]?\\d{4}")
    private val pickCode = 100
    private val permCode = 200

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val root = LinearLayout(this)
        root.orientation = LinearLayout.VERTICAL
        root.setPadding(dp(20), dp(20), dp(20), dp(20))

        val pickBtn = Button(this)
        pickBtn.text = "사진 선택해서 번호 찾기"
        pickBtn.setOnClickListener { pickImage() }

        status = TextView(this)
        status.text = "사진을 선택하세요"
        status.setPadding(0, dp(12), 0, dp(12))

        phoneInput = EditText(this)
        phoneInput.hint = "전화번호"
        phoneInput.inputType = InputType.TYPE_CLASS_PHONE

        messageInput = EditText(this)
        messageInput.hint = "보낼 문자 내용"
        messageInput.minLines = 4
        messageInput.gravity = Gravity.TOP
        messageInput.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
        messageInput.setText(prefs.getString("msg", ""))

        autoCheck = CheckBox(this)
        autoCheck.text = "번호가 1개만 인식되면 바로 자동 발송"
        autoCheck.isChecked = prefs.getBoolean("auto", false)

        val sendBtn = Button(this)
        sendBtn.text = "문자 보내기"
        sendBtn.setOnClickListener { sendSms() }

        root.addView(pickBtn)
        root.addView(status)
        root.addView(phoneInput)
        root.addView(messageInput)
        root.addView(autoCheck)
        root.addView(sendBtn)

        val scroll = ScrollView(this)
        scroll.addView(root)
        setContentView(scroll)

        if (checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.SEND_SMS), permCode)
        }
    }

    private fun pickImage() {
        val i = Intent(Intent.ACTION_GET_CONTENT)
        i.type = "image/*"
        startActivityForResult(Intent.createChooser(i, "사진 선택"), pickCode)
    }

    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == pickCode && resultCode == RESULT_OK) {
            data?.data?.let { recognize(it) }
        }
    }

    private fun recognize(uri: Uri) {
        status.text = "인식 중..."
        try {
            val image = InputImage.fromFilePath(this, uri)
            recognizer.process(image)
                .addOnSuccessListener { result ->
                    val numbers = phoneRegex.findAll(result.text)
                        .map { m -> m.value.filter { it.isDigit() } }
                        .distinct()
                        .toList()
                    if (numbers.isEmpty()) {
                        status.text = "전화번호를 찾지 못했어요. 직접 입력해 주세요."
                    } else {
                        phoneInput.setText(numbers[0])
                        status.text = if (numbers.size == 1) {
                            "번호를 찾았어요"
                        } else {
                            "번호 ${numbers.size}개 발견: ${numbers.joinToString(", ")}\n첫 번째 번호를 넣었어요. 확인 후 보내세요."
                        }
                        if (autoCheck.isChecked && numbers.size == 1) sendSms()
                    }
                }
                .addOnFailureListener { status.text = "인식 실패: ${it.message}" }
        } catch (e: Exception) {
            status.text = "사진을 열 수 없어요: ${e.message}"
        }
    }

    @Suppress("DEPRECATION")
    private fun sendSms() {
        val phone = phoneInput.text.toString().filter { it.isDigit() }
        val msg = messageInput.text.toString().trim()
        if (phone.length < 10) {
            Toast.makeText(this, "전화번호를 확인하세요", Toast.LENGTH_SHORT).show()
            return
        }
        if (msg.isEmpty()) {
            Toast.makeText(this, "문자 내용을 입력하세요", Toast.LENGTH_SHORT).show()
            return
        }
        if (checkSelfPermission(Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.SEND_SMS), permCode)
            status.text = "문자 권한을 허용한 뒤 다시 눌러주세요"
            return
        }
        prefs.edit().putString("msg", msg).putBoolean("auto", autoCheck.isChecked).apply()
        try {
            val sms = if (Build.VERSION.SDK_INT >= 31) {
                getSystemService(SmsManager::class.java)
            } else {
                SmsManager.getDefault()
            }
            val parts = sms.divideMessage(msg)
            sms.sendMultipartTextMessage(phone, null, parts, null, null)
            status.text = "$phone 번호로 발송 요청했어요"
        } catch (e: Exception) {
            status.text = "발송 실패: ${e.message}"
        }
    }
}
