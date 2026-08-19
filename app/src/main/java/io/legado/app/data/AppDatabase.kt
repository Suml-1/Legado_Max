package io.legado.app.data

import android.content.ContentValues
import android.database.sqlite.SQLiteDatabase
import android.os.Build
import android.util.Log
import androidx.room.AutoMigration
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.sqlite.db.SupportSQLiteDatabase
import io.legado.app.data.dao.BookChapterDao
import io.legado.app.data.dao.BookDao
import io.legado.app.data.dao.BookGroupDao
import io.legado.app.data.dao.BookSourceDao
import io.legado.app.data.dao.BookmarkDao
import io.legado.app.data.dao.CacheDao
import io.legado.app.data.dao.CookieDao
import io.legado.app.data.dao.CoverGalleryDao
import io.legado.app.data.dao.DictRuleDao
import io.legado.app.data.dao.HomepageCustomSetDao
import io.legado.app.data.dao.HomepageCustomSetMemberDao
import io.legado.app.data.dao.HomepageModuleDao
import io.legado.app.data.dao.HomepageModulePrefDao
import io.legado.app.data.dao.HomepageUserModuleDao
import io.legado.app.data.dao.HttpTTSDao
import io.legado.app.data.dao.KeyboardAssistsDao
import io.legado.app.data.dao.ReadRecordDao
import io.legado.app.data.dao.ReplaceRuleDao
import io.legado.app.data.dao.RssArticleDao
import io.legado.app.data.dao.RssReadRecordDao
import io.legado.app.data.dao.RssSourceDao
import io.legado.app.data.dao.RssStarDao
import io.legado.app.data.dao.RuleSubDao
import io.legado.app.data.dao.SearchBookDao
import io.legado.app.data.dao.SearchKeywordDao
import io.legado.app.data.dao.ServerDao
import io.legado.app.data.dao.SourceRecycleBinDao
import io.legado.app.data.dao.TxtTocRuleDao
import io.legado.app.data.dao.UrlRecordDao
import io.legado.app.data.dao.DirectLinkUploadRuleDao
import io.legado.app.data.dao.UploadHistoryDao
import io.legado.app.data.entities.Book
import io.legado.app.data.entities.BookChapter
import io.legado.app.data.entities.BookGroup
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.data.entities.Bookmark
import io.legado.app.data.entities.Cache
import io.legado.app.data.entities.Cookie
import io.legado.app.data.entities.CoverGalleryGroup
import io.legado.app.data.entities.CoverGalleryImage
import io.legado.app.data.entities.DictRule
import io.legado.app.data.entities.DirectLinkUploadRule
import io.legado.app.data.entities.HomepageCustomSet
import io.legado.app.data.entities.HomepageCustomSetMember
import io.legado.app.data.entities.HomepageModule
import io.legado.app.data.entities.HomepageModulePref
import io.legado.app.data.entities.HomepageUserModule
import io.legado.app.data.entities.HttpTTS
import io.legado.app.data.entities.KeyboardAssist
import io.legado.app.data.entities.ReplaceRule
import io.legado.app.data.entities.readRecord.ReadRecord
import io.legado.app.data.entities.readRecord.ReadRecordDetail
import io.legado.app.data.entities.readRecord.ReadRecordSession
import io.legado.app.data.entities.RssArticle
import io.legado.app.data.entities.RssReadRecord
import io.legado.app.data.entities.RssSource
import io.legado.app.data.entities.RssStar
import io.legado.app.data.entities.RuleSub
import io.legado.app.data.entities.SearchBook
import io.legado.app.data.entities.SearchKeyword
import io.legado.app.data.entities.Server
import io.legado.app.data.entities.SourceRecycleBin
import io.legado.app.data.entities.TxtTocRule
import io.legado.app.data.entities.UploadHistory
import io.legado.app.data.entities.UrlRecord
import io.legado.app.help.DefaultData
import org.intellij.lang.annotations.Language
import splitties.init.appCtx
import java.util.Locale

val appDb by lazy {
    Room.databaseBuilder(appCtx, AppDatabase::class.java, AppDatabase.DATABASE_NAME)
        .fallbackToDestructiveMigrationFrom(false, 1, 2, 3, 4, 5, 6, 7, 8, 9)  
        .addMigrations(*DatabaseMigrations.migrations)
        .allowMainThreadQueries()
        .addCallback(AppDatabase.dbCallback)
        .build()
}

@Database(
    version = 101,
    exportSchema = true,
    entities = [Book::class, BookGroup::class, BookSource::class, BookChapter::class,
        ReplaceRule::class, SearchBook::class, SearchKeyword::class, Cookie::class,
        RssSource::class, Bookmark::class, RssArticle::class, RssReadRecord::class,
        RssStar::class, TxtTocRule::class, ReadRecord::class, ReadRecordDetail::class,
        ReadRecordSession::class, HttpTTS::class, Cache::class,
        RuleSub::class, DictRule::class, KeyboardAssist::class, Server::class,
        UrlRecord::class, DirectLinkUploadRule::class, UploadHistory::class,
        CoverGalleryGroup::class, CoverGalleryImage::class, SourceRecycleBin::class,
        HomepageModule::class, HomepageCustomSet::class,
        HomepageModulePref::class, HomepageUserModule::class, HomepageCustomSetMember::class],
    views = [BookSourcePart::class],
    autoMigrations = [
        AutoMigration(from = 43, to = 44),
        AutoMigration(from = 44, to = 45),
        AutoMigration(from = 45, to = 46),
        AutoMigration(from = 46, to = 47),
        AutoMigration(from = 47, to = 48),
        AutoMigration(from = 48, to = 49),
        AutoMigration(from = 49, to = 50),
        AutoMigration(from = 50, to = 51),
        AutoMigration(from = 51, to = 52),
        AutoMigration(from = 52, to = 53),
        AutoMigration(from = 53, to = 54),
        AutoMigration(from = 54, to = 55, spec = DatabaseMigrations.Migration_54_55::class),
        AutoMigration(from = 55, to = 56),
        AutoMigration(from = 56, to = 57),
        AutoMigration(from = 57, to = 58),
        AutoMigration(from = 58, to = 59),
        AutoMigration(from = 59, to = 60),
        AutoMigration(from = 60, to = 61),
        AutoMigration(from = 61, to = 62),
        AutoMigration(from = 62, to = 63),
        AutoMigration(from = 63, to = 64),
        AutoMigration(from = 64, to = 65, spec = DatabaseMigrations.Migration_64_65::class),
        AutoMigration(from = 65, to = 66),
        AutoMigration(from = 66, to = 67),
        AutoMigration(from = 67, to = 68),
        AutoMigration(from = 68, to = 69),
        AutoMigration(from = 69, to = 70),
        AutoMigration(from = 70, to = 71),
        AutoMigration(from = 71, to = 72),
        AutoMigration(from = 72, to = 73),
        AutoMigration(from = 73, to = 74),
        AutoMigration(from = 74, to = 75),
        AutoMigration(from = 75, to = 76),
        AutoMigration(from = 76, to = 77),
        AutoMigration(from = 77, to = 78),
        AutoMigration(from = 78, to = 79),
        AutoMigration(from = 79, to = 80),
        AutoMigration(from = 80, to = 81, spec = DatabaseMigrations.Migration_80_81::class),
        AutoMigration(from = 81, to = 82),
        AutoMigration(from = 82, to = 83),
        AutoMigration(from = 83, to = 84, spec = DatabaseMigrations.Migration_83_84::class),
        AutoMigration(from = 84, to = 85, spec = DatabaseMigrations.Migration_84_85::class),
        AutoMigration(from = 85, to = 86),
        AutoMigration(from = 86, to = 87),
        AutoMigration(from = 87, to = 88),
        AutoMigration(from = 88, to = 89),
        AutoMigration(from = 89, to = 90),
        AutoMigration(from = 90, to = 91, spec = DatabaseMigrations.Migration_90_91::class),
        AutoMigration(from = 91, to = 92),
        AutoMigration(from = 92, to = 93),
        AutoMigration(from = 93, to = 94),
        AutoMigration(from = 94, to = 95)
    ]
)
abstract class AppDatabase : RoomDatabase() {

    abstract val bookDao: BookDao
    abstract val bookGroupDao: BookGroupDao
    abstract val bookSourceDao: BookSourceDao
    abstract val bookChapterDao: BookChapterDao
    abstract val replaceRuleDao: ReplaceRuleDao
    abstract val searchBookDao: SearchBookDao
    abstract val searchKeywordDao: SearchKeywordDao
    abstract val rssSourceDao: RssSourceDao
    abstract val bookmarkDao: BookmarkDao
    abstract val rssArticleDao: RssArticleDao
    abstract val rssStarDao: RssStarDao
    abstract val rssReadRecordDao: RssReadRecordDao
    abstract val cookieDao: CookieDao
    abstract val txtTocRuleDao: TxtTocRuleDao
    abstract val readRecordDao: ReadRecordDao
    abstract val httpTTSDao: HttpTTSDao
    abstract val cacheDao: CacheDao
    abstract val ruleSubDao: RuleSubDao
    abstract val dictRuleDao: DictRuleDao
    abstract val keyboardAssistsDao: KeyboardAssistsDao
    abstract val serverDao: ServerDao
    abstract val sourceRecycleBinDao: SourceRecycleBinDao
    abstract val urlRecordDao: UrlRecordDao
    abstract val directLinkUploadRuleDao: DirectLinkUploadRuleDao
    abstract val uploadHistoryDao: UploadHistoryDao
    abstract val coverGalleryDao: CoverGalleryDao
    abstract val homepageModuleDao: HomepageModuleDao
    abstract val homepageCustomSetDao: HomepageCustomSetDao
    abstract val homepageModulePrefDao: HomepageModulePrefDao
    abstract val homepageUserModuleDao: HomepageUserModuleDao
    abstract val homepageCustomSetMemberDao: HomepageCustomSetMemberDao

    companion object {

        const val DATABASE_NAME = "legado.db"

        const val BOOK_TABLE_NAME = "books"
        const val BOOK_SOURCE_TABLE_NAME = "book_sources"
        const val RSS_SOURCE_TABLE_NAME = "rssSources"
        const val BOOK_GROUP_TABLE_NAME = "book_groups"

        val dbCallback = object : Callback() {

            override fun onCreate(db: SupportSQLiteDatabase) {
                // 只在 API 级别 23 (Marshmallow) 及以上版本尝试设置区域设置
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    try {
                        Log.d("AppDatabaseCallback", "准备 设置 locale for API ${Build.VERSION.SDK_INT}...")
                        db.setLocale(Locale.CHINESE)
                        // 在 21 上报错，但无法拦截
                        Log.d("AppDatabaseCallback", "成功 设置 locale for API ${Build.VERSION.SDK_INT}.")
                    } catch (e: Exception) {
                        Log.e("AppDatabaseCallback", "错误 设置 locale in onCreate for API ${Build.VERSION.SDK_INT}", e)
                    }
                } else {
                    Log.i("AppDatabaseCallback", "跳过 setLocale for API ${Build.VERSION.SDK_INT} (below M).")
                }
            }

            override fun onOpen(db: SupportSQLiteDatabase) {
                // onOpen 中的操作均为最佳努力性质的维护操作，
                // 任何单个操作失败不应阻止数据库正常使用。
                // 逐个包裹 try-catch 确保单个失败不影响后续流程。

                val bookGroupSqls = listOf(
                    """insert into book_groups(groupId, groupName, 'order', show) 
                        select ${BookGroup.IdAll}, '全部', -10, 1
                        where not exists (select * from book_groups where groupId = ${BookGroup.IdAll})"""
                        .trimIndent(),
                    """insert into book_groups(groupId, groupName, 'order', enableRefresh, show) 
                        select ${BookGroup.IdLocal}, '本地', -9, 0, 1
                        where not exists (select * from book_groups where groupId = ${BookGroup.IdLocal})"""
                        .trimIndent(),
                    """insert into book_groups(groupId, groupName, 'order', show) 
                        select ${BookGroup.IdAudio}, '音频', -8, 1
                        where not exists (select * from book_groups where groupId = ${BookGroup.IdAudio})"""
                        .trimIndent(),
                    """insert into book_groups(groupId, groupName, 'order', show) 
                        select ${BookGroup.IdNetNone}, '网络未分组', -7, 1
                        where not exists (select * from book_groups where groupId = ${BookGroup.IdNetNone})"""
                        .trimIndent(),
                    """insert into book_groups(groupId, groupName, 'order', show) 
                        select ${BookGroup.IdLocalNone}, '本地未分组', -6, 0
                        where not exists (select * from book_groups where groupId = ${BookGroup.IdLocalNone})"""
                        .trimIndent(),
                    """insert into book_groups(groupId, groupName, 'order', show) 
                        select ${BookGroup.IdVideo}, '视频', -5, 1
                        where not exists (select * from book_groups where groupId = ${BookGroup.IdVideo})"""
                        .trimIndent(),
                    """insert into book_groups(groupId, groupName, 'order', show) 
                        select ${BookGroup.IdError}, '更新失败', -1, 1
                        where not exists (select * from book_groups where groupId = ${BookGroup.IdError})"""
                        .trimIndent()
                )
                for (sql in bookGroupSqls) {
                    try {
                        db.execSQL(sql)
                    } catch (e: Exception) {
                        Log.e("AppDatabaseCallback", "onOpen: 插入默认分组失败", e)
                    }
                }

                // 清理 loginUi 中的 'null' 字符串值（旧版本遗留数据）
                val cleanupSqls = listOf(
                    "update book_sources set loginUi = null where loginUi = 'null'",
                    "update rssSources set loginUi = null where loginUi = 'null'",
                    "update httpTTS set loginUi = null where loginUi = 'null'",
                    "update httpTTS set concurrentRate = '0' where concurrentRate is null"
                )
                for (sql in cleanupSqls) {
                    try {
                        db.execSQL(sql)
                    } catch (e: Exception) {
                        Log.e("AppDatabaseCallback", "onOpen: 清理数据失败", e)
                    }
                }

                // 初始化键盘辅助数据
                try {
                    db.query("select * from keyboardAssists order by serialNo").use {
                        if (it.count == 0) {
                            DefaultData.keyboardAssists.forEach { keyboardAssist ->
                                val contentValues = ContentValues().apply {
                                    put("type", keyboardAssist.type)
                                    put("key", keyboardAssist.key)
                                    put("value", keyboardAssist.value)
                                    put("serialNo", keyboardAssist.serialNo)
                                }
                                db.insert(
                                    "keyboardAssists",
                                    SQLiteDatabase.CONFLICT_REPLACE,
                                    contentValues
                                )
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("AppDatabaseCallback", "onOpen: 初始化键盘辅助数据失败", e)
                }
            }
        }

    }

}
