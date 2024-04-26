package com.lyc.downloader.db

import android.os.Parcel
import android.os.Parcelable
import org.greenrobot.greendao.annotation.Entity
import org.greenrobot.greendao.annotation.Index
import org.greenrobot.greendao.annotation.Property
import java.util.*

@Entity(indexes = [Index(value = "url DESC")])
data class DownloadInfo2(
    val id: Long,
    val string: String,
    val path: String,
    val filename: String,
    var resumable: Boolean = false,
    var downloadSize: Long,
    @Property(nameInDb = "total_size") val totalSize: Long,
    val lastModified: String,
    val createTime: Date,
    var updateTime: Date,
    var state: Int,
    val errorCode: Int,
    @Transient val daoSession: DaoSession
): Parcelable {
    constructor(parcel: Parcel) : this(
        parcel.readLong(),
        parcel.readString(),
        parcel.readString(),
        parcel.readString(),
        parcel.readByte() != 0.toByte(),
        parcel.readLong(),
        parcel.readLong(),
        parcel.readString(),
        TODO("createTime"),
        TODO("updateTime"),
        parcel.readInt(),
        parcel.readInt(),
        TODO("daoSession")
    ) {
    }

    override fun writeToParcel(parcel: Parcel, flags: Int) {
        parcel.writeLong(id)
        parcel.writeString(string)
        parcel.writeString(path)
        parcel.writeString(filename)
        parcel.writeByte(if (resumable) 1 else 0)
        parcel.writeLong(downloadSize)
        parcel.writeLong(totalSize)
        parcel.writeString(lastModified)
        parcel.writeInt(state)
        parcel.writeInt(errorCode)
    }

    override fun describeContents(): Int {
        return 0
    }

    fun readFromParcel(parcel: Parcel) {

    }

    companion object CREATOR : Parcelable.Creator<DownloadInfo2> {
        override fun createFromParcel(parcel: Parcel): DownloadInfo2 {
            return DownloadInfo2(parcel)
        }

        override fun newArray(size: Int): Array<DownloadInfo2?> {
            return arrayOfNulls(size)
        }
    }
}
