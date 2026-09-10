package com.muradgalayev.brainbuddy.data.contacts

import android.content.Context
import android.provider.ContactsContract
import android.telephony.PhoneNumberUtils
import android.telephony.TelephonyManager
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.nio.charset.StandardCharsets
import java.security.MessageDigest
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

data class DevicePhoneContact(
    val contactId: String,
    val displayName: String,
    // Kept on-device so an invite can be addressed through the SMS app.
    val phoneNumber: String,
    val phoneHash: String,
)

@Singleton
class DeviceContactsRepository @Inject constructor(
    @param:ApplicationContext private val context: Context,
) {
    // Called only after READ_CONTACTS has been granted. Contact names and raw phone numbers remain
    // inside this process; TogetherRepository sends only the fixed-size hashes to Supabase.
    suspend fun loadPhoneContacts(): List<DevicePhoneContact> = withContext(Dispatchers.IO) {
        val countryIso = deviceCountryIso()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.CONTACT_ID,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY,
            ContactsContract.CommonDataKinds.Phone.NUMBER,
            ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER,
        )

        val contacts = mutableListOf<DevicePhoneContact>()
        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY + " ASC",
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.CONTACT_ID)
            val nameColumn = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME_PRIMARY)
            val numberColumn = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            val normalizedColumn = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NORMALIZED_NUMBER)

            while (cursor.moveToNext() && contacts.size < MAX_PHONE_NUMBERS) {
                if (idColumn < 0 || numberColumn < 0) continue
                val rawNumber = cursor.getString(numberColumn) ?: continue
                val providerNormalized = normalizedColumn.takeIf { it >= 0 }
                    ?.let(cursor::getString)
                val normalized = normalizeContactPhone(
                    raw = rawNumber,
                    providerNormalized = providerNormalized,
                    countryIso = countryIso,
                ) ?: continue
                val displayName = nameColumn.takeIf { it >= 0 }
                    ?.let(cursor::getString)
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() }
                    ?: normalized

                contacts += DevicePhoneContact(
                    contactId = cursor.getLong(idColumn).toString(),
                    displayName = displayName,
                    phoneNumber = rawNumber,
                    phoneHash = sha256Phone(normalized),
                )
            }
        }

        // One contact can expose the same number through several synced address-book accounts.
        contacts.distinctBy { it.phoneHash }
    }

    private fun deviceCountryIso(): String {
        val simCountry = runCatching {
            context.getSystemService(TelephonyManager::class.java)?.simCountryIso
        }.getOrNull().orEmpty()
        return simCountry.ifBlank { Locale.getDefault().country }
            .uppercase(Locale.ROOT)
    }

    private companion object {
        // A hard client cap mirrors the backend cap and bounds memory/request size on unusually
        // large corporate address books.
        const val MAX_PHONE_NUMBERS = 2_000
    }
}

internal fun normalizeContactPhone(
    raw: String,
    providerNormalized: String?,
    countryIso: String,
): String? {
    fun validE164(value: String?): String? {
        val candidate = value?.trim().orEmpty()
        return candidate.takeIf { E164.matches(it) }
    }

    validE164(providerNormalized)?.let { return it }

    val dialable = PhoneNumberUtils.normalizeNumber(raw)
    validE164(dialable)?.let { return it }

    return runCatching { PhoneNumberUtils.formatNumberToE164(raw, countryIso) }
        .getOrNull()
        ?.let(::validE164)
}

internal fun sha256Phone(normalizedPhone: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(normalizedPhone.toByteArray(StandardCharsets.UTF_8))
        .joinToString(separator = "") { byte -> "%02x".format(byte.toInt() and 0xff) }

private val E164 = Regex("^\\+[1-9]\\d{7,14}$")
