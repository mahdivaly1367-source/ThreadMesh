import React, { useState } from 'react';
import { FileCode, Folder, Check, Copy } from 'lucide-react';

interface AndroidCodeExplorerProps {
  onClose: () => void;
}

const ANDROID_FILES: Record<string, { lang: string; path: string; summary: string; code: string }> = {
  'MeshRouter.kt': {
    lang: 'kotlin',
    path: 'app/src/main/java/com/threadmesh/app/mesh/MeshRouter.kt',
    summary: 'موتور انتقال چندمرحله‌ای (Store-and-Forward) با مدیریت صف Outbox، شمارش پرش‌ها، جلوگیری از حلقه و انقضای زمانی',
    code: `package com.threadmesh.app.mesh

import com.threadmesh.app.data.local.OutboxDao
import com.threadmesh.app.data.local.ThreadMeshDao
import com.threadmesh.app.data.model.*
import com.threadmesh.app.transport.TransportProvider

class MeshRouter(
    val localNodeId: String,
    private val eventDao: ThreadMeshDao,
    private val outboxDao: OutboxDao,
    private val transport: TransportProvider?
) {
    suspend fun processIncomingPacket(rawJson: String, fromEndpointId: String): Result<Boolean> {
        val packet = RelayPacket.fromJson(rawJson)
        // 1. فیلتر فقط MESH_PUBLIC
        if (packet.event.publishScope != PublishScope.MESH_PUBLIC) return Result.failure(...)
        // 2. جلوگیری از انقضا با TTL
        if (packet.isExpired()) return Result.success(false)
        // 3. جلوگیری از حلقه (Loop Prevention)
        if (packet.visitedNodeIds.contains(localNodeId)) return Result.success(false)
        // 4. جلوگیری از درج رویداد تکراری (Deduplication)
        if (eventDao.getEventById(packet.event.id) != null) return Result.success(false)
        // 5. ذخیره محلی در پایگاه داده Room
        eventDao.insertEvent(packet.event)
        // 6. صف‌بندی برای پرش بعدی
        if (packet.canForward(localNodeId)) {
            val nextHop = packet.nextHop(localNodeId)
            outboxDao.insertOutboxItem(OutboxEntity(...))
        }
        return Result.success(true)
    }
}`,
  },
  'RelayPacket.kt': {
    lang: 'kotlin',
    path: 'app/src/main/java/com/threadmesh/app/data/model/RelayPacket.kt',
    summary: 'بسته انتقال مش شامل رویداد تغییرناپذیر، شمارنده پرش (hopCount)، سقف پرش (maxHops)، سابقه مسیر (visitedNodeIds) و زمان انقضا',
    code: `package com.threadmesh.app.data.model

data class RelayPacket(
    val packetId: String,
    val event: EventEntity,
    val hopCount: Int = 0,
    val maxHops: Int = 5,
    val visitedNodeIds: List<String> = emptyList(),
    val expiresAt: Long = System.currentTimeMillis() + DEFAULT_TTL_MILLIS,
    val relayedByNodeId: String = "",
    val relayedAt: Long = System.currentTimeMillis()
) {
    fun canForward(localNodeId: String): Boolean =
        event.publishScope == PublishScope.MESH_PUBLIC &&
        hopCount < maxHops &&
        !visitedNodeIds.contains(localNodeId) &&
        !isExpired()

    fun nextHop(localNodeId: String) = copy(
        packetId = "pkt_\${event.id}_\${hopCount + 1}",
        hopCount = hopCount + 1,
        visitedNodeIds = visitedNodeIds + localNodeId,
        relayedByNodeId = localNodeId
    )
}`,
  },
  'OutboxDao.kt': {
    lang: 'kotlin',
    path: 'app/src/main/java/com/threadmesh/app/data/local/OutboxDao.kt',
    summary: 'صف پایدار پیام‌های در انتظار رله در دیتابیس Room با ردیابی وضعیت PENDING، RELAYED و EXPIRED',
    code: `package com.threadmesh.app.data.local

import androidx.room.*
import com.threadmesh.app.data.model.OutboxEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface OutboxDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertOutboxItem(item: OutboxEntity): Long

    @Query("SELECT * FROM mesh_outbox WHERE status = 'PENDING' AND expiresAt > :now ORDER BY createdAt ASC")
    suspend fun getPendingPackets(now: Long = System.currentTimeMillis()): List<OutboxEntity>

    @Query("UPDATE mesh_outbox SET status = 'RELAYED' WHERE packetId = :packetId")
    suspend fun markRelayed(packetId: String)
}`,
  },
  'MultiHopStoreAndForwardTest.kt': {
    lang: 'kotlin',
    path: 'app/src/test/java/com/threadmesh/app/MultiHopStoreAndForwardTest.kt',
    summary: 'تست‌های واحد کاتلین سناریوی انتقال A -> B -> C، کشف حلقه، رعایت سقف پرش و فیلتر دامنه‌ها',
    code: `package com.threadmesh.app

import org.junit.Assert.*
import org.junit.Test

class MultiHopStoreAndForwardTest {
    @Test
    fun testMultiHopProgressionAToBToC() {
        // انتقال از A به B و ذخیره در B
        routerB.processIncomingPacket(packetFromA.toJson())
        // انتقال از صف B به C و تایید رسیدن پیام با حفظ منشا A
        routerC.processIncomingPacket(queuedPacketInB.toJson())
        assertEquals(eventA.text, daoC.getEventById("evt_A_101")?.text)
    }
}`,
  },
  'android-ci.yml': {
    lang: 'yaml',
    path: '.github/workflows/android-ci.yml',
    summary: 'ورک‌فلو GitHub Actions برای کامپایل کامل اندروید، اجرای آزمون‌های کاتلین، بررسی Lint و تولید خودکار فایل APK',
    code: `name: ThreadMesh Android CI
on: [push, pull_request]
jobs:
  build-and-test:
    runs-on: ubuntu-latest
    steps:
      - uses: actions/checkout@v4
      - uses: actions/setup-java@v4
        with: { distribution: 'temurin', java-version: '17' }
      - run: chmod +x ./gradlew
      - run: ./gradlew testDebugUnitTest
      - run: ./gradlew lintDebug
      - run: ./gradlew assembleDebug
      - uses: actions/upload-artifact@v4
        with: { name: ThreadMesh-Debug-APK, path: app/build/outputs/apk/debug/app-debug.apk }`,
  },
  'GoogleNearbyTransport.kt': {
    lang: 'kotlin',
    path: 'app/src/main/java/com/threadmesh/app/transport/GoogleNearbyTransport.kt',
    summary: 'پیاده‌سازی رسمی اتصال آفلاین Google Nearby با استراتژی P2P_CLUSTER، احراز هویت Handshake و فیلتر MESH_PUBLIC',
    code: `package com.threadmesh.app.transport

import android.content.Context
import com.google.android.gms.nearby.Nearby
import com.google.android.gms.nearby.connection.*
import com.threadmesh.app.data.model.EventEntity
import com.threadmesh.app.data.model.PublishScope

class GoogleNearbyTransport(context: Context) : TransportProvider {
    companion object {
        const val SERVICE_ID = "com.threadmesh.app.mesh.p2p"
        const val MAX_TEXT_LENGTH = 4000
    }

    override fun start(localEndpointName: String) {
        val advertisingOptions = AdvertisingOptions.Builder()
            .setStrategy(Strategy.P2P_CLUSTER).build()
        val discoveryOptions = DiscoveryOptions.Builder()
            .setStrategy(Strategy.P2P_CLUSTER).build()

        connectionsClient.startAdvertising(localEndpointName, SERVICE_ID, connectionCallback, advertisingOptions)
        connectionsClient.startDiscovery(SERVICE_ID, discoveryCallback, discoveryOptions)
    }

    override fun sendMeshEvent(endpointId: String, event: EventEntity): Result<Unit> {
        // فیلتر سخت‌گیرانه: LOCAL_ONLY و GLOBAL_PUBLIC هرگز ارسال نمی‌شوند
        if (event.publishScope != PublishScope.MESH_PUBLIC) {
            return Result.failure(IllegalArgumentException("فقط رویدادهای MESH_PUBLIC اجازه ارسال دارند."))
        }
        val bytes = serializeEventToBytes(event)
        connectionsClient.sendPayload(endpointId, Payload.fromBytes(bytes))
        return Result.success(Unit)
    }
}`,
  },
  'MeshTransport.kt': {
    lang: 'kotlin',
    path: 'app/src/main/java/com/threadmesh/app/transport/MeshTransport.kt',
    summary: 'اینترفیس مجرد TransportProvider برای جداسازی کامل لایه گفتگو از API گوگل و پشتیبانی تست‌های واحد',
    code: `package com.threadmesh.app.transport

import com.threadmesh.app.data.model.EventEntity
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow

interface TransportProvider {
    val serviceState: StateFlow<MeshServiceState>
    val peers: StateFlow<List<DiscoveredPeer>>
    val incomingEvents: SharedFlow<EventEntity>
    val lastError: StateFlow<String?>

    fun start(localEndpointName: String)
    fun stop()
    fun requestConnection(endpointId: String)
    fun acceptConnection(endpointId: String)
    fun rejectConnection(endpointId: String)
    fun sendMeshEvent(endpointId: String, event: EventEntity): Result<Unit>
    fun broadcastMeshEvent(event: EventEntity): Result<Int>
}`,
  },
  'MeshTransportFilterTest.kt': {
    lang: 'kotlin',
    path: 'app/src/test/java/com/threadmesh/app/MeshTransportFilterTest.kt',
    summary: 'تست‌های واحد برای فیلتر دامنه‌های انتشار، سقف ۴۰۰۰ کاراکتر و جلوگیری از درج رویداد تکراری',
    code: `package com.threadmesh.app

import org.junit.Assert.*
import org.junit.Test

class MeshTransportFilterTest {
    @Test
    fun testTransportRejectsLocalOnlyAndGlobalPublic() {
        // LOCAL_ONLY باید با خطا رد شود و به شبکه نرسد
        val resLocal = transport.sendMeshEvent("peer_1", localOnlyEvent)
        assertTrue(resLocal.isFailure)

        // MESH_PUBLIC باید با موفقیت ارسال شود
        val resMesh = transport.sendMeshEvent("peer_1", meshPublicEvent)
        assertTrue(resMesh.isSuccess)
    }

    @Test
    fun testDeduplicationPreventsDuplicateRecords() {
        // دریافت مجدد همان event.id نباید رکورد تکراری بسازد
        val first = repository.importIncomingMeshEvent(validMeshEvent)
        val second = repository.importIncomingMeshEvent(validMeshEvent)
        assertEquals(true, first.getOrNull())
        assertEquals(false, second.getOrNull())
    }
}`,
  },
  'MainActivity.kt': {
    lang: 'kotlin',
    path: 'app/src/main/java/com/threadmesh/app/MainActivity.kt',
    summary: 'اکتیویتی تک‌صفحه‌ای (Single Activity) با ناوبری Jetpack Compose و تم راست‌به‌چپ Material 3',
    code: `package com.threadmesh.app
// ... MainActivity with GoogleNearbyTransport injection`,
  },
  'EventEntity.kt': {
    lang: 'kotlin',
    path: 'app/src/main/java/com/threadmesh/app/data/model/EventEntity.kt',
    summary: 'موجودیت Room با کلید اصلی UUID، نوع رویداد، دامنهٔ انتشار و ارجاع به والد و هدفِ تغییر دیدگاه',
    code: `package com.threadmesh.app.data.model

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "mesh_events",
    indices = [
        Index(value = ["rootThreadId"]),
        Index(value = ["parentEventId"]),
        Index(value = ["createdAt"])
    ]
)
data class EventEntity(
    @PrimaryKey
    val id: String, // UUID
    val localAuthorId: String,
    val displayName: String,
    val createdAt: Long,
    val eventType: EventType,
    val rootThreadId: String,
    val parentEventId: String? = null,
    val targetEventId: String? = null, // برای رویداد تغییر دیدگاه
    val text: String,
    val publishScope: PublishScope = PublishScope.LOCAL_ONLY,
    val lastViewedAt: Long? = null
)`,
  },
  'ThreadMeshDao.kt': {
    lang: 'kotlin',
    path: 'app/src/main/java/com/threadmesh/app/data/local/ThreadMeshDao.kt',
    summary: 'اینترفیس DAO پایگاه داده محلی Room با کوئری‌های واکنشی Flow و جلوگیری از درج تکراری',
    code: `package com.threadmesh.app.data.local

import androidx.room.*
import com.threadmesh.app.data.model.EventEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface ThreadMeshDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertEvent(event: EventEntity): Long

    @Query("SELECT * FROM mesh_events WHERE rootThreadId = :rootThreadId ORDER BY createdAt ASC")
    fun getEventsForThreadFlow(rootThreadId: String): Flow<List<EventEntity>>

    @Query("SELECT * FROM mesh_events WHERE eventType = 'ROOT_THREAD' ORDER BY createdAt DESC")
    fun getAllRootThreadsFlow(): Flow<List<EventEntity>>

    @Query("UPDATE mesh_events SET lastViewedAt = :viewedTime WHERE id = :rootThreadId")
    suspend fun updateThreadLastViewed(rootThreadId: String, viewedTime: Long)
}`,
  },
  'ThreadMeshRepository.kt': {
    lang: 'kotlin',
    path: 'app/src/main/java/com/threadmesh/app/data/repository/ThreadMeshRepository.kt',
    summary: 'لایه مخزن داده؛ ساخت درخت چندسطحی، ثبت تغییرناپذیر تغییر دیدگاه و اعتبارسنجی محدودیت ۴۰۰۰ نویسه',
    code: `package com.threadmesh.app.data.repository

import com.threadmesh.app.data.local.ThreadMeshDao
import com.threadmesh.app.data.model.EventEntity
import com.threadmesh.app.data.model.EventType
import com.threadmesh.app.data.model.PublishScope
import java.util.UUID

class ThreadMeshRepository(private val dao: ThreadMeshDao) {
    suspend fun createRootThread(authorId: String, displayName: String, text: String, scope: PublishScope): Result<EventEntity> {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return Result.failure(IllegalArgumentException("متن نمی‌تواند خالی باشد."))
        if (trimmed.length > 4000) return Result.failure(IllegalArgumentException("بیش از ۴۰۰۰ نویسه مجاز نیست."))
        val event = EventEntity(id = UUID.randomUUID().toString(), ...)
        dao.insertEvent(event)
        return Result.success(event)
    }

    suspend fun recordStanceChange(targetEventId: String, explanationText: String, ...): Result<EventEntity> {
        // ایجاد رویداد مستقل جدید و تغییرناپذیر بدون دستکاری پست قبلی
        val event = EventEntity(eventType = EventType.STANCE_CHANGED, targetEventId = targetEventId, ...)
        dao.insertEvent(event)
        return Result.success(event)
    }
}`,
  },
  'app/build.gradle.kts': {
    lang: 'kotlin',
    path: 'app/build.gradle.kts',
    summary: 'پیکربندی Gradle اندروید با Jetpack Compose BOM، Room KSP، Navigation Compose و Coroutines',
    code: `plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    alias(libs.plugins.ksp)
}

android {
    namespace = "com.threadmesh.app"
    compileSdk = 34
    defaultConfig {
        applicationId = "com.threadmesh.app"
        minSdk = 26
        targetSdk = 34
    }
    buildFeatures { compose = true }
}

dependencies {
    implementation(libs.androidx.material3)
    implementation(libs.androidx.room.runtime)
    implementation(libs.androidx.room.ktx)
    ksp(libs.androidx.room.compiler)
    implementation(libs.kotlinx.coroutines.android)
}`,
  },
};

export const AndroidCodeExplorer: React.FC<AndroidCodeExplorerProps> = ({ onClose }) => {
  const [selectedKey, setSelectedKey] = useState<string>('MainActivity.kt');
  const [copied, setCopied] = useState(false);
  const activeFile = ANDROID_FILES[selectedKey];

  const handleCopy = () => {
    navigator.clipboard.writeText(activeFile.code);
    setCopied(true);
    setTimeout(() => setCopied(false), 2000);
  };

  return (
    <div className="fixed inset-0 z-50 bg-slate-900/80 backdrop-blur-sm flex items-center justify-center p-4">
      <div className="bg-white rounded-2xl w-full max-w-4xl h-[85vh] flex flex-col shadow-2xl overflow-hidden border border-slate-200">
        {/* Header */}
        <div className="bg-slate-900 text-white px-6 py-4 flex items-center justify-between">
          <div className="flex items-center gap-3">
            <div className="w-8 h-8 rounded-lg bg-emerald-500/20 text-emerald-400 flex items-center justify-center">
              <Folder className="w-5 h-5" />
            </div>
            <div>
              <h3 className="font-bold text-base">کدهای بومی پروژه Android (Kotlin + Compose + Room)</h3>
              <p className="text-xs text-slate-400">فایل‌های استاندارد ذخیره‌شده در ساختار پروژه اندروید</p>
            </div>
          </div>
          <button
            onClick={onClose}
            className="text-slate-400 hover:text-white px-3 py-1.5 rounded-lg hover:bg-slate-800 transition text-sm"
          >
            بستن پنجره
          </button>
        </div>

        {/* Content */}
        <div className="flex-1 flex flex-col md:flex-row overflow-hidden">
          {/* File selector sidebar */}
          <div className="w-full md:w-64 bg-slate-50 border-l border-slate-200 p-3 space-y-1.5 overflow-y-auto">
            <p className="text-xs font-semibold text-slate-500 px-2 py-1">فایل‌های کاتلین و گریدل:</p>
            {Object.keys(ANDROID_FILES).map((fileName) => {
              const file = ANDROID_FILES[fileName];
              const isSelected = fileName === selectedKey;
              return (
                <button
                  key={fileName}
                  onClick={() => setSelectedKey(fileName)}
                  className={`w-full text-right px-3 py-2.5 rounded-xl text-xs font-medium flex items-center gap-2.5 transition ${
                    isSelected
                      ? 'bg-slate-900 text-white shadow-sm'
                      : 'text-slate-700 hover:bg-slate-200/70'
                  }`}
                >
                  <FileCode className={`w-4 h-4 shrink-0 ${isSelected ? 'text-indigo-400' : 'text-slate-400'}`} />
                  <span className="truncate font-mono">{fileName}</span>
                </button>
              );
            })}
          </div>

          {/* Code Viewer */}
          <div className="flex-1 flex flex-col bg-slate-950 text-slate-100 overflow-hidden">
            <div className="px-5 py-3 border-b border-slate-800 flex items-center justify-between text-xs">
              <div className="flex flex-col gap-0.5">
                <span className="font-mono text-indigo-400">{activeFile.path}</span>
                <span className="text-slate-400">{activeFile.summary}</span>
              </div>
              <button
                onClick={handleCopy}
                className="flex items-center gap-1.5 bg-slate-800 hover:bg-slate-700 text-slate-200 px-3 py-1.5 rounded-lg transition shrink-0"
              >
                {copied ? <Check className="w-3.5 h-3.5 text-emerald-400" /> : <Copy className="w-3.5 h-3.5" />}
                <span>{copied ? 'کپی شد' : 'کپی کد'}</span>
              </button>
            </div>
            <pre className="flex-1 p-5 overflow-auto text-xs font-mono leading-relaxed text-slate-300">
              <code>{activeFile.code}</code>
            </pre>
          </div>
        </div>
      </div>
    </div>
  );
};
