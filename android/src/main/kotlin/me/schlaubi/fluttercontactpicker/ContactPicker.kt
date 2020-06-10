package me.schlaubi.fluttercontactpicker

import android.app.Activity
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.ContactsContract
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.PluginRegistry

class ContactPicker private constructor(private val pickContext: PickContext, private val result: MethodChannel.Result) : PluginRegistry.ActivityResultListener {

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?): Boolean {
        if (data == null) {
            pickContext.removeActivityResultListener(this)
            return false
        }

        when (requestCode) {
            FlutterContactPickerPlugin.PICK_EMAIL -> processContact(data, "email", ::buildEmailAddress)
            FlutterContactPickerPlugin.PICK_PHONE -> processContact(data, "phoneNumber", ::buildPhoneNumber)
            else -> return false
        }
        return true
    }

    private fun processContact(intent: Intent, dataName: String, dataProcessor: (Cursor, Activity) -> Map<String, String>) {
        val data = intent.data!!
        val activity = pickContext.activity
        activity.contentResolver.query(data, null, null, null, null).use {
            require(it != null) { "Cursor must not be null" }
            it.moveToFirst()
            val processedData = dataProcessor(it, activity)
            val fullName = it.getString(it.getColumnIndex(ContactsContract.CommonDataKinds.Identity.DISPLAY_NAME))
            val result = mapOf("fullName" to fullName, dataName to processedData)
            this.result.success(result)
        }
        pickContext.removeActivityResultListener(this)
    }

    private fun buildPhoneNumber(cursor: Cursor, activity: Activity): Map<String, String> {
        val phoneType = cursor.getInt(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE))
        val customLabel = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LABEL))
        val avatar=cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.PHOTO_THUMBNAIL_URI))
        val label = ContactsContract.CommonDataKinds.Phone.getTypeLabel(activity.resources, phoneType, customLabel) as String
        val number = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER))
        return mapOf("phoneNumber" to number, label(label))
    }

    private fun buildEmailAddress(cursor: Cursor, activity: Activity): Map<String, String> {
        val phoneType = cursor.getInt(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.TYPE))
        val customLabel = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.LABEL))
        val label = ContactsContract.CommonDataKinds.Email.getTypeLabel(activity.resources, phoneType, customLabel) as String
        val address = cursor.getString(cursor.getColumnIndex(ContactsContract.CommonDataKinds.Email.DATA))
        return mapOf("email" to address, label(label))
    }

    private fun label(label: String) = "label" to label

    companion object {
        fun requestPicker(requestCode: Int, type: Uri, result: MethodChannel.Result, context: PickContext) {
            val picker = ContactPicker(context, result)
            val pickerIntent = Intent(Intent.ACTION_PICK, type)
            context.addActivityResultListener(picker)
            context.activity.startActivityForResult(pickerIntent, requestCode)
        }
    }
}
