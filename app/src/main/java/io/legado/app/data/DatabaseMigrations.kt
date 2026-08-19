package io.legado.app.data

import androidx.room.DeleteColumn
import androidx.room.migration.AutoMigrationSpec
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import io.legado.app.constant.AppConst
import io.legado.app.constant.BookSourceType
import io.legado.app.constant.BookType

object DatabaseMigrations {

    val migrations: Array<Migration> by lazy {
        arrayOf(
            migration_10_11, migration_11_12, migration_12_13, migration_13_14,
            migration_14_15, migration_15_17, migration_17_18, migration_18_19,
            migration_19_20, migration_20_21, migration_21_22, migration_22_23,
            migration_23_24, migration_24_25, migration_25_26, migration_26_27,
            migration_27_28, migration_28_29, migration_29_30, migration_30_31,
            migration_31_32, migration_32_33, migration_33_34, migration_34_35,
            migration_35_36, migration_36_37, migration_37_38, migration_38_39,
            migration_39_40, migration_40_41, migration_41_42, migration_42_43,
            migration_95_96, migration_96_97, migration_97_98, migration_98_99,
            migration_99_100, migration_100_101
        )
    }

    private val migration_10_11 = object : Migration(10, 11) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE txtTocRules")
            db.execSQL(
                """CREATE TABLE txtTocRules(id INTEGER NOT NULL, 
                    name TEXT NOT NULL, rule TEXT NOT NULL, serialNumber INTEGER NOT NULL, 
                    enable INTEGER NOT NULL, PRIMARY KEY (id))"""
            )
        }
    }

    private val migration_11_12 = object : Migration(11, 12) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE rssSources ADD style TEXT ")
        }
    }

    private val migration_12_13 = object : Migration(12, 13) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE rssSources ADD articleStyle INTEGER NOT NULL DEFAULT 0 ")
        }
    }

    private val migration_13_14 = object : Migration(13, 14) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `books_new` (`bookUrl` TEXT NOT NULL, `tocUrl` TEXT NOT NULL, `origin` TEXT NOT NULL,
                    `originName` TEXT NOT NULL, `name` TEXT NOT NULL, `author` TEXT NOT NULL, `kind` TEXT, `customTag` TEXT, `coverUrl` TEXT, 
                    `customCoverUrl` TEXT, `intro` TEXT, `customIntro` TEXT, `charset` TEXT, `type` INTEGER NOT NULL, `group` INTEGER NOT NULL, 
                    `latestChapterTitle` TEXT, `latestChapterTime` INTEGER NOT NULL, `lastCheckTime` INTEGER NOT NULL, `lastCheckCount` INTEGER NOT NULL, 
                    `totalChapterNum` INTEGER NOT NULL, `durChapterTitle` TEXT, `durChapterIndex` INTEGER NOT NULL, `durChapterPos` INTEGER NOT NULL, 
                    `durChapterTime` INTEGER NOT NULL, `wordCount` TEXT, `canUpdate` INTEGER NOT NULL, `order` INTEGER NOT NULL, 
                    `originOrder` INTEGER NOT NULL, `useReplaceRule` INTEGER NOT NULL, `variable` TEXT, PRIMARY KEY(`bookUrl`))"""
            )
            db.execSQL("INSERT INTO books_new select * from books ")
            db.execSQL("DROP TABLE books")
            db.execSQL("ALTER TABLE books_new RENAME TO books")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_books_name_author` ON `books` (`name`, `author`) ")
        }
    }

    private val migration_14_15 = object : Migration(14, 15) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE bookmarks ADD bookAuthor TEXT NOT NULL DEFAULT ''")
        }
    }

    private val migration_15_17 = object : Migration(15, 17) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `readRecord` (`bookName` TEXT NOT NULL, `readTime` INTEGER NOT NULL, PRIMARY KEY(`bookName`))")
        }
    }

    private val migration_17_18 = object : Migration(17, 18) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `httpTTS` (`id` INTEGER NOT NULL, `name` TEXT NOT NULL, `url` TEXT NOT NULL, PRIMARY KEY(`id`))")
        }
    }

    private val migration_18_19 = object : Migration(18, 19) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `readRecordNew` (`androidId` TEXT NOT NULL, `bookName` TEXT NOT NULL, `readTime` INTEGER NOT NULL, 
                    PRIMARY KEY(`androidId`, `bookName`))"""
            )
            db.execSQL("INSERT INTO readRecordNew(androidId, bookName, readTime) select '${AppConst.androidId}' as androidId, bookName, readTime from readRecord")
            db.execSQL("DROP TABLE readRecord")
            db.execSQL("ALTER TABLE readRecordNew RENAME TO readRecord")
        }
    }
    private val migration_19_20 = object : Migration(19, 20) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE book_sources ADD bookSourceComment TEXT")
        }
    }

    private val migration_20_21 = object : Migration(20, 21) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE book_groups ADD show INTEGER NOT NULL DEFAULT 1")
        }
    }

    private val migration_21_22 = object : Migration(21, 22) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `books_new` (`bookUrl` TEXT NOT NULL, `tocUrl` TEXT NOT NULL, `origin` TEXT NOT NULL, 
                    `originName` TEXT NOT NULL, `name` TEXT NOT NULL, `author` TEXT NOT NULL, `kind` TEXT, `customTag` TEXT, 
                    `coverUrl` TEXT, `customCoverUrl` TEXT, `intro` TEXT, `customIntro` TEXT, `charset` TEXT, `type` INTEGER NOT NULL, 
                    `group` INTEGER NOT NULL, `latestChapterTitle` TEXT, `latestChapterTime` INTEGER NOT NULL, `lastCheckTime` INTEGER NOT NULL, 
                    `lastCheckCount` INTEGER NOT NULL, `totalChapterNum` INTEGER NOT NULL, `durChapterTitle` TEXT, `durChapterIndex` INTEGER NOT NULL, 
                    `durChapterPos` INTEGER NOT NULL, `durChapterTime` INTEGER NOT NULL, `wordCount` TEXT, `canUpdate` INTEGER NOT NULL, 
                    `order` INTEGER NOT NULL, `originOrder` INTEGER NOT NULL, `variable` TEXT, `readConfig` TEXT, PRIMARY KEY(`bookUrl`))"""
            )
            db.execSQL(
                """INSERT INTO books_new select `bookUrl`, `tocUrl`, `origin`, `originName`, `name`, `author`, `kind`, `customTag`, `coverUrl`, 
                    `customCoverUrl`, `intro`, `customIntro`, `charset`, `type`, `group`, `latestChapterTitle`, `latestChapterTime`, `lastCheckTime`, 
                    `lastCheckCount`, `totalChapterNum`, `durChapterTitle`, `durChapterIndex`, `durChapterPos`, `durChapterTime`, `wordCount`, `canUpdate`, 
                    `order`, `originOrder`, `variable`, null
                    from books"""
            )
            db.execSQL("DROP TABLE books")
            db.execSQL("ALTER TABLE books_new RENAME TO books")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_books_name_author` ON `books` (`name`, `author`) ")
        }
    }

    private val migration_22_23 = object : Migration(22, 23) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE chapters ADD baseUrl TEXT NOT NULL DEFAULT ''")
        }
    }

    private val migration_23_24 = object : Migration(23, 24) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("CREATE TABLE IF NOT EXISTS `caches` (`key` TEXT NOT NULL, `value` TEXT, `deadline` INTEGER NOT NULL, PRIMARY KEY(`key`))")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_caches_key` ON `caches` (`key`)")
        }
    }

    private val migration_24_25 = object : Migration(24, 25) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `sourceSubs` 
                    (`id` INTEGER NOT NULL, `name` TEXT NOT NULL, `url` TEXT NOT NULL, `type` INTEGER NOT NULL, `customOrder` INTEGER NOT NULL, 
                    PRIMARY KEY(`id`))"""
            )
        }
    }

    private val migration_25_26 = object : Migration(25, 26) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `ruleSubs` (`id` INTEGER NOT NULL, `name` TEXT NOT NULL, `url` TEXT NOT NULL, `type` INTEGER NOT NULL, 
                    `customOrder` INTEGER NOT NULL, `autoUpdate` INTEGER NOT NULL, `update` INTEGER NOT NULL, PRIMARY KEY(`id`))"""
            )
            db.execSQL(" insert into `ruleSubs` select *, 0, 0 from `sourceSubs` ")
            db.execSQL("DROP TABLE `sourceSubs`")
        }
    }

    private val migration_26_27 = object : Migration(26, 27) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(" ALTER TABLE rssSources ADD singleUrl INTEGER NOT NULL DEFAULT 0 ")
            db.execSQL(
                """CREATE TABLE IF NOT EXISTS `bookmarks1` (`time` INTEGER NOT NULL, `bookUrl` TEXT NOT NULL, `bookName` TEXT NOT NULL, 
                        `bookAuthor` TEXT NOT NULL, `chapterIndex` INTEGER NOT NULL, `chapterPos` INTEGER NOT NULL, `chapterName` TEXT NOT NULL, 
                        `bookText` TEXT NOT NULL, `content` TEXT NOT NULL, PRIMARY KEY(`time`))"""
            )
            db.execSQL(
                """insert into `bookmarks1` 
                        select `time`, `bookUrl`, `bookName`, `bookAuthor`, `chapterIndex`, `pageIndex`, `chapterName`, '', `content` 
                        from bookmarks"""
            )
            db.execSQL(" DROP TABLE `bookmarks` ")
            db.execSQL(" ALTER TABLE bookmarks1 RENAME TO bookmarks ")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_bookmarks_time` ON `bookmarks` (`time`)")
        }
    }

    private val migration_27_28 = object : Migration(27, 28) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE rssArticles ADD variable TEXT")
            db.execSQL("ALTER TABLE rssStars ADD variable TEXT")
        }
    }

    private val migration_28_29 = object : Migration(28, 29) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE rssSources ADD sourceComment TEXT")
        }
    }

    private val migration_29_30 = object : Migration(29, 30) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE chapters ADD `startFragmentId` TEXT")
            db.execSQL("ALTER TABLE chapters ADD `endFragmentId` TEXT")
            db.execSQL(
                """
                    CREATE TABLE IF NOT EXISTS `epubChapters` 
                    (`bookUrl` TEXT NOT NULL, `href` TEXT NOT NULL, `parentHref` TEXT, 
                    PRIMARY KEY(`bookUrl`, `href`), FOREIGN KEY(`bookUrl`) REFERENCES `books`(`bookUrl`) ON UPDATE NO ACTION ON DELETE CASCADE )
                """
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_epubChapters_bookUrl` ON `epubChapters` (`bookUrl`)")
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_epubChapters_bookUrl_href` ON `epubChapters` (`bookUrl`, `href`)")
        }
    }

    private val migration_30_31 = object : Migration(30, 31) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE readRecord RENAME TO readRecord1")
            db.execSQL(
                """
                    CREATE TABLE IF NOT EXISTS `readRecord` (`deviceId` TEXT NOT NULL, `bookName` TEXT NOT NULL, `readTime` INTEGER NOT NULL, PRIMARY KEY(`deviceId`, `bookName`))
                """
            )
            db.execSQL("insert into readRecord (deviceId, bookName, readTime) select androidId, bookName, readTime from readRecord1")
        }
    }

    private val migration_31_32 = object : Migration(31, 32) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("DROP TABLE `epubChapters`")
        }
    }

    private val migration_32_33 = object : Migration(32, 33) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE bookmarks RENAME TO bookmarks_old")
            db.execSQL(
                """
                    CREATE TABLE IF NOT EXISTS `bookmarks` (`time` INTEGER NOT NULL,
                    `bookName` TEXT NOT NULL, `bookAuthor` TEXT NOT NULL, `chapterIndex` INTEGER NOT NULL, 
                    `chapterPos` INTEGER NOT NULL, `chapterName` TEXT NOT NULL, `bookText` TEXT NOT NULL, 
                    `content` TEXT NOT NULL, PRIMARY KEY(`time`))
                """
            )
            db.execSQL(
                """
                    CREATE INDEX IF NOT EXISTS `index_bookmarks_bookName_bookAuthor` ON `bookmarks` (`bookName`, `bookAuthor`)
                """
            )
            db.execSQL(
                """
                    insert into bookmarks (time, bookName, bookAuthor, chapterIndex, chapterPos, chapterName, bookText, content)
                    select time, ifNull(b.name, bookName) bookName, ifNull(b.author, bookAuthor) bookAuthor, 
                    chapterIndex, chapterPos, chapterName, bookText, content from bookmarks_old o
                    left join books b on o.bookUrl = b.bookUrl
                """
            )
        }
    }

    private val migration_33_34 = object : Migration(33, 34) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `book_groups` ADD `cover` TEXT")
        }
    }

    private val migration_34_35 = object : Migration(34, 35) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `book_sources` ADD `concurrentRate` TEXT")
        }
    }

    private val migration_35_36 = object : Migration(35, 36) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `book_sources` ADD `loginUi` TEXT")
            db.execSQL("ALTER TABLE `book_sources` ADD`loginCheckJs` TEXT")
        }
    }

    private val migration_36_37 = object : Migration(36, 37) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `rssSources` ADD `loginUrl` TEXT")
            db.execSQL("ALTER TABLE `rssSources` ADD `loginUi` TEXT")
            db.execSQL("ALTER TABLE `rssSources` ADD `loginCheckJs` TEXT")
        }
    }

    private val migration_37_38 = object : Migration(37, 38) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `book_sources` ADD `respondTime` INTEGER NOT NULL DEFAULT 180000")
        }
    }

    private val migration_38_39 = object : Migration(38, 39) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `rssSources` ADD `concurrentRate` TEXT")
        }
    }

    private val migration_39_40 = object : Migration(39, 40) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `chapters` ADD `isVip` INTEGER NOT NULL DEFAULT 0")
            db.execSQL("ALTER TABLE `chapters` ADD `isPay` INTEGER NOT NULL DEFAULT 0")
        }
    }

    private val migration_40_41 = object : Migration(40, 41) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `httpTTS` ADD `loginUrl` TEXT")
            db.execSQL("ALTER TABLE `httpTTS` ADD `loginUi` TEXT")
            db.execSQL("ALTER TABLE `httpTTS` ADD `loginCheckJs` TEXT")
            db.execSQL("ALTER TABLE `httpTTS` ADD `header` TEXT")
            db.execSQL("ALTER TABLE `httpTTS` ADD `concurrentRate` TEXT")
        }
    }

    private val migration_41_42 = object : Migration(41, 42) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE 'httpTTS' ADD `contentType` TEXT")
        }
    }

    private val migration_42_43 = object : Migration(42, 43) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE `chapters` ADD `isVolume` INTEGER NOT NULL DEFAULT 0")
        }
    }


    @Suppress("ClassName")
    class Migration_54_55 : AutoMigrationSpec {

        override fun onPostMigrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                update books set type = ${BookType.audio}
                where type = ${BookSourceType.audio}
            """.trimIndent()
            )
            db.execSQL(
                """
                update books set type = ${BookType.image}
                where type = ${BookSourceType.image}
            """.trimIndent()
            )
            db.execSQL(
                """
                update books set type = ${BookType.webFile}
                where type = ${BookSourceType.file}
            """.trimIndent()
            )
            db.execSQL(
                """
                update books set type = ${BookType.text}
                where type = ${BookSourceType.default}
            """.trimIndent()
            )
            db.execSQL(
                """
                update books set type = type | ${BookType.local}
                where origin like '${BookType.localTag}%' or origin like '${BookType.webDavTag}%'
            """.trimIndent()
            )
        }

    }


    @Suppress("ClassName")
    @DeleteColumn(
        tableName = "book_sources",
        columnName = "enabledReview"
    )
    class Migration_64_65 : AutoMigrationSpec

    @Suppress("ClassName")
    class Migration_80_81 : AutoMigrationSpec {
        override fun onPostMigrate(db: SupportSQLiteDatabase) {
            db.execSQL("""
            CREATE TABLE rssArticles_new (
                origin TEXT NOT NULL DEFAULT '',
                sort TEXT NOT NULL DEFAULT '',
                title TEXT NOT NULL DEFAULT '',
                `order` INTEGER NOT NULL DEFAULT 0,
                link TEXT NOT NULL DEFAULT '',
                pubDate TEXT,
                description TEXT,
                content TEXT,
                image TEXT,
                `group` TEXT NOT NULL DEFAULT '默认分组',
                read INTEGER NOT NULL DEFAULT 0,
                variable TEXT,
                PRIMARY KEY (origin, link, sort)
            )
        """.trimIndent())
            db.execSQL("""
            INSERT INTO rssArticles_new (origin, sort, title, `order`, link, pubDate, description, content, image, `group`, read, variable)
            SELECT origin, sort, title, `order`, link, pubDate, description, content, image, `group`, read, variable FROM rssArticles
        """.trimIndent())
            db.execSQL("DROP TABLE rssArticles")
            db.execSQL("ALTER TABLE rssArticles_new RENAME TO rssArticles")
        }
    }

    @Suppress("ClassName")
    @DeleteColumn(
        tableName = "rssArticles",
        columnName = "ratio"
    )
    class Migration_83_84 : AutoMigrationSpec

    @Suppress("ClassName")
    @DeleteColumn(
        tableName = "chapters",
        columnName = "lyric"
    )
    @DeleteColumn(
        tableName = "chapters",
        columnName = "reviewImg"
    )
    class Migration_84_85 : AutoMigrationSpec

    @Suppress("ClassName")
    @DeleteColumn.Entries(
        DeleteColumn(
            tableName = "readRecord",
            columnName = "firstRead"
        )
    )
    class Migration_90_91 : AutoMigrationSpec {
        override fun onPostMigrate(db: SupportSQLiteDatabase) {
            db.beginTransaction()
            try {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS readRecord_new (
                        deviceId TEXT NOT NULL,
                        bookName TEXT NOT NULL,
                        bookAuthor TEXT NOT NULL DEFAULT '',
                        readTime INTEGER NOT NULL DEFAULT 0,
                        lastRead INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(deviceId, bookName, bookAuthor)
                    )
                """.trimIndent())
                db.execSQL("""
                    INSERT OR IGNORE INTO readRecord_new (deviceId, bookName, bookAuthor, readTime, lastRead)
                    SELECT deviceId, bookName, '', SUM(readTime), MAX(COALESCE(lastRead, 0))
                    FROM readRecord
                    GROUP BY deviceId, bookName
                """.trimIndent())
                db.execSQL("DROP TABLE IF EXISTS readRecord")
                db.execSQL("ALTER TABLE readRecord_new RENAME TO readRecord")
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS readRecordDetail (
                        deviceId TEXT NOT NULL,
                        bookName TEXT NOT NULL,
                        bookAuthor TEXT NOT NULL DEFAULT '',
                        date TEXT NOT NULL,
                        readTime INTEGER NOT NULL DEFAULT 0,
                        readWords INTEGER NOT NULL DEFAULT 0,
                        firstReadTime INTEGER NOT NULL DEFAULT 0,
                        lastReadTime INTEGER NOT NULL DEFAULT 0,
                        PRIMARY KEY(deviceId, bookName, bookAuthor, date)
                    )
                """.trimIndent())
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS readRecordSession (
                        id INTEGER PRIMARY KEY AUTOINCREMENT,
                        deviceId TEXT NOT NULL,
                        bookName TEXT NOT NULL,
                        bookAuthor TEXT NOT NULL DEFAULT '',
                        startTime INTEGER NOT NULL,
                        endTime INTEGER NOT NULL,
                        words INTEGER NOT NULL DEFAULT 0
                    )
                """.trimIndent())
                db.setTransactionSuccessful()
            } finally {
                db.endTransaction()
            }
        }
    }

    private val migration_95_96 = object : Migration(95, 96) {
        override fun migrate(db: SupportSQLiteDatabase) {
            val cursor = db.query("PRAGMA table_info(book_sources)")
            var hasNextPagePreload = false
            var hasNextPageLazyLoad = false
            while (cursor.moveToNext()) {
                val columnName = cursor.getString(cursor.getColumnIndexOrThrow("name"))
                if (columnName == "nextPagePreload") hasNextPagePreload = true
                if (columnName == "nextPageLazyLoad") hasNextPageLazyLoad = true
            }
            cursor.close()
            when {
                !hasNextPagePreload && !hasNextPageLazyLoad -> {
                    db.execSQL("ALTER TABLE book_sources ADD COLUMN nextPageLazyLoad INTEGER NOT NULL DEFAULT 0")
                }
                hasNextPagePreload && !hasNextPageLazyLoad -> {
                    db.beginTransaction()
                    try {
                        db.execSQL("""
                            CREATE TABLE IF NOT EXISTS `book_sources_new` (
                                `bookSourceUrl` TEXT NOT NULL, `bookSourceName` TEXT NOT NULL, `bookSourceGroup` TEXT,
                                `bookSourceType` INTEGER NOT NULL, `bookUrlPattern` TEXT, `customOrder` INTEGER NOT NULL DEFAULT 0,
                                `enabled` INTEGER NOT NULL DEFAULT 1, `enabledExplore` INTEGER NOT NULL DEFAULT 1, `jsLib` TEXT,
                                `enabledCookieJar` INTEGER DEFAULT 0, `concurrentRate` TEXT, `header` TEXT, `loginUrl` TEXT,
                                `loginUi` TEXT, `loginCheckJs` TEXT, `coverDecodeJs` TEXT, `bookSourceComment` TEXT,
                                `variableComment` TEXT, `lastUpdateTime` INTEGER NOT NULL, `respondTime` INTEGER NOT NULL,
                                `weight` INTEGER NOT NULL, `exploreUrl` TEXT, `exploreScreen` TEXT, `ruleExplore` TEXT,
                                `searchUrl` TEXT, `ruleSearch` TEXT, `ruleBookInfo` TEXT, `ruleToc` TEXT, `ruleContent` TEXT,
                                `ruleReview` TEXT, `eventListener` INTEGER NOT NULL DEFAULT 0, `customButton` INTEGER NOT NULL DEFAULT 0,
                                `nextPageLazyLoad` INTEGER NOT NULL DEFAULT 0, PRIMARY KEY(`bookSourceUrl`)
                            )
                        """.trimIndent())
                        db.execSQL("""
                            INSERT INTO `book_sources_new` SELECT
                                `bookSourceUrl`, `bookSourceName`, `bookSourceGroup`, `bookSourceType`, `bookUrlPattern`,
                                `customOrder`, `enabled`, `enabledExplore`, `jsLib`, `enabledCookieJar`, `concurrentRate`,
                                `header`, `loginUrl`, `loginUi`, `loginCheckJs`, `coverDecodeJs`, `bookSourceComment`,
                                `variableComment`, `lastUpdateTime`, `respondTime`, `weight`, `exploreUrl`, `exploreScreen`,
                                `ruleExplore`, `searchUrl`, `ruleSearch`, `ruleBookInfo`, `ruleToc`, `ruleContent`,
                                `ruleReview`, `eventListener`, `customButton`, `nextPagePreload`
                            FROM `book_sources`
                        """.trimIndent())
                        db.execSQL("DROP TABLE `book_sources`")
                        db.execSQL("ALTER TABLE `book_sources_new` RENAME TO `book_sources`")
                        db.execSQL("CREATE INDEX IF NOT EXISTS `index_book_sources_bookSourceUrl` ON `book_sources` (`bookSourceUrl`)")
                        db.setTransactionSuccessful()
                    } finally {
                        db.endTransaction()
                    }
                }
            }
        }
    }

    // =======================================================================
    // 96 → 97：readRecordSession 表新增 durChapterTitle 列
    // 记录每次阅读会话结束时所在的章节名称，用于阅读记录详情页按会话显示章节
    // =======================================================================
    private val migration_96_97 = object : Migration(96, 97) {
        override fun migrate(db: SupportSQLiteDatabase) {
            val cursor = db.query("PRAGMA table_info(readRecordSession)")
            var hasDurChapterTitle = false
            while (cursor.moveToNext()) {
                if (cursor.getString(cursor.getColumnIndexOrThrow("name")) == "durChapterTitle") {
                    hasDurChapterTitle = true
                    break
                }
            }
            cursor.close()
            if (!hasDurChapterTitle) {
                db.execSQL("ALTER TABLE readRecordSession ADD COLUMN durChapterTitle TEXT NOT NULL DEFAULT ''")
            }
        }
    }

    private val migration_97_98 = object : Migration(97, 98) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `cover_gallery_groups` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `name` TEXT NOT NULL,
                    `isDefault` INTEGER NOT NULL,
                    `order` INTEGER NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    `updatedAt` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `cover_gallery_images` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `groupId` INTEGER NOT NULL,
                    `path` TEXT NOT NULL,
                    `order` INTEGER NOT NULL,
                    `createdAt` INTEGER NOT NULL,
                    FOREIGN KEY(`groupId`) REFERENCES `cover_gallery_groups`(`id`)
                    ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_cover_gallery_images_groupId` ON `cover_gallery_images` (`groupId`)"
            )
        }
    }

    private val migration_98_99 = object : Migration(98, 99) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `source_recycle_bin` (
                    `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                    `type` TEXT NOT NULL,
                    `key` TEXT NOT NULL,
                    `name` TEXT NOT NULL,
                    `groupName` TEXT,
                    `payload` TEXT NOT NULL,
                    `deletedAt` INTEGER NOT NULL,
                    `expireAt` INTEGER NOT NULL
                )
                """.trimIndent()
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_source_recycle_bin_type` ON `source_recycle_bin` (`type`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_source_recycle_bin_key` ON `source_recycle_bin` (`key`)"
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_source_recycle_bin_expireAt` ON `source_recycle_bin` (`expireAt`)"
            )
        }
    }

    // =======================================================================
    // 99 → 100：创建首页模块相关表 + book_sources 表新增 homepageModules 列
    // homepage_modules 表用于存储首页模块配置
    // homepage_custom_sets 表用于存储用户自定义集
    // book_sources 表新增 homepageModules 列，用于存储书源关联的首页模块定义
    // =======================================================================
    private val migration_99_100 = object : Migration(99, 100) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 创建首页模块表
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `homepage_modules` (
                    `id` TEXT NOT NULL PRIMARY KEY,
                    `sourceUrl` TEXT NOT NULL,
                    `moduleKey` TEXT NOT NULL,
                    `type` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `args` TEXT,
                    `layoutConfig` TEXT,
                    `url` TEXT,
                    `isEnabled` INTEGER NOT NULL DEFAULT 1,
                    `sortOrder` INTEGER NOT NULL DEFAULT 0,
                    `customSetId` TEXT,
                    `isUserCreated` INTEGER NOT NULL DEFAULT 0,
                    `customTitle` TEXT,
                    `customSetTitle` TEXT,
                    `sourceJsonHash` TEXT,
                    `syncedAt` INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
            // 创建首页自定义集表
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `homepage_custom_sets` (
                    `id` TEXT NOT NULL PRIMARY KEY,
                    `name` TEXT NOT NULL,
                    `sortOrder` INTEGER NOT NULL DEFAULT 0
                )
                """.trimIndent()
            )
            // book_sources 表新增 homepageModules 列
            // 使用 PRAGMA table_info 检查列是否已存在，避免在旧版本 SQLite 上因重复添加导致迁移失败
            var hasHomepageModules = false
            try {
                db.query("SELECT name FROM pragma_table_info('book_sources') WHERE name='homepageModules'").use { cursor ->
                    hasHomepageModules = cursor.moveToFirst()
                }
            } catch (_: Exception) {
                // 如果 PRAGMA 查询失败（极老的 SQLite 版本），尝试直接 ALTER，
                // 让 SQLite 自身的 "duplicate column name" 错误来判断
            }
            if (!hasHomepageModules) {
                try {
                    db.execSQL("ALTER TABLE book_sources ADD COLUMN homepageModules TEXT DEFAULT ''")
                } catch (e: Exception) {
                    // 列可能已存在（多进程并发迁移等极端场景），忽略错误继续
                    android.util.Log.e("DatabaseMigrations", "99→100: 添加 homepageModules 列失败", e)
                }
            }
        }
    }

    // =======================================================================
    // 100 → 101：首页模块架构重构 — 消除中间表，改为实时视图 + 轻量偏好层
    //
    // 新增三张表：
    //   homepage_module_prefs — 书源同步模块的用户偏好（显隐/排序/自定义标题/集归属）
    //   homepage_user_modules — 用户手动创建的模块（按钮组/排行榜等）
    //   homepage_custom_set_members — 自定义集成员关系（引用关系，不复制数据）
    //
    // 旧表 homepage_modules 和 homepage_custom_sets 保留（向后兼容），
    // 但新代码不再使用 homepage_modules 表，改从书源 homepageModules JSON 实时解析。
    // homepage_custom_sets 表继续用于自定义集（cs_ 前缀），src_/rss_ 前缀集由书源表自动生成。
    //
    // 数据迁移策略：
    //   1. 用户偏好：从旧 homepage_modules 表提取 isEnabled/customTitle/sortOrder/customSetId
    //   2. 用户创建模块：从旧 homepage_modules 表提取 isUserCreated=1 的记录
    //   3. 自定义集成员：从旧 homepage_modules 表提取 customSetId 为 cs_ 前缀的模块关系
    //   4. 旧表保留不删，避免破坏旧版本回退能力
    // =======================================================================
    private val migration_100_101 = object : Migration(100, 101) {
        override fun migrate(db: SupportSQLiteDatabase) {
            // 1. 创建 homepage_module_prefs 表
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `homepage_module_prefs` (
                    `sourceUrl` TEXT NOT NULL,
                    `moduleKey` TEXT NOT NULL,
                    `isEnabled` INTEGER NOT NULL DEFAULT 1,
                    `customTitle` TEXT,
                    `sortOrder` INTEGER NOT NULL DEFAULT 0,
                    `customSetId` TEXT,
                    PRIMARY KEY(`sourceUrl`, `moduleKey`),
                    FOREIGN KEY(`sourceUrl`) REFERENCES `book_sources`(`bookSourceUrl`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_homepage_module_prefs_sourceUrl` ON `homepage_module_prefs` (`sourceUrl`)")

            // 2. 创建 homepage_user_modules 表（无外键约束，因为 sourceUrl 可能引用 book_sources 或 rssSources）
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `homepage_user_modules` (
                    `id` TEXT NOT NULL PRIMARY KEY,
                    `sourceUrl` TEXT NOT NULL,
                    `moduleKey` TEXT NOT NULL,
                    `type` TEXT NOT NULL,
                    `title` TEXT NOT NULL,
                    `args` TEXT,
                    `layoutConfig` TEXT,
                    `url` TEXT,
                    `isEnabled` INTEGER NOT NULL DEFAULT 1,
                    `customSetId` TEXT,
                    `sortOrder` INTEGER NOT NULL DEFAULT 0,
                    `sourceType` TEXT NOT NULL DEFAULT 'book'
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_homepage_user_modules_sourceUrl` ON `homepage_user_modules` (`sourceUrl`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_homepage_user_modules_customSetId` ON `homepage_user_modules` (`customSetId`)")

            // 3. 创建 homepage_custom_set_members 表
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `homepage_custom_set_members` (
                    `customSetId` TEXT NOT NULL,
                    `sourceUrl` TEXT NOT NULL,
                    `moduleKey` TEXT NOT NULL,
                    `sortOrder` INTEGER NOT NULL DEFAULT 0,
                    PRIMARY KEY(`customSetId`, `sourceUrl`, `moduleKey`),
                    FOREIGN KEY(`sourceUrl`) REFERENCES `book_sources`(`bookSourceUrl`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent()
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_homepage_custom_set_members_sourceUrl` ON `homepage_custom_set_members` (`sourceUrl`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_homepage_custom_set_members_customSetId` ON `homepage_custom_set_members` (`customSetId`)")

            // 4. 开启外键约束（Room 在数据库打开时会自动开启，但迁移期间需要手动确保）
            db.execSQL("PRAGMA foreign_keys = ON")

            // 5. 数据迁移：从旧 homepage_modules 表迁移到新表
            //    注意：迁移前先确保只迁移 book 类型书源的模块（RSS 源模块的 sourceUrl 不在 book_sources 表中，
            //    外键约束会导致插入失败，因此 RSS 源模块跳过外键关联，仅迁移到 user_modules 表）

            // 5a. 迁移用户偏好（所有模块的 isEnabled/customTitle/sortOrder）
            //     仅迁移 sourceUrl 在 book_sources 表中存在的记录（满足外键约束）
            db.execSQL(
                """
                INSERT OR IGNORE INTO `homepage_module_prefs` (`sourceUrl`, `moduleKey`, `isEnabled`, `customTitle`, `sortOrder`, `customSetId`)
                SELECT `sourceUrl`, `moduleKey`, `isEnabled`, `customTitle`, `sortOrder`,
                    CASE WHEN `customSetId` LIKE 'src_%' OR `customSetId` LIKE 'rss_%' THEN NULL
                         ELSE `customSetId` END
                FROM `homepage_modules`
                WHERE `sourceUrl` IN (SELECT `bookSourceUrl` FROM `book_sources`)
                """.trimIndent()
            )

            // 5b. 迁移用户手动创建的模块（isUserCreated = 1）
            //     仅迁移 sourceUrl 在 book_sources 表中存在的记录
            db.execSQL(
                """
                INSERT OR IGNORE INTO `homepage_user_modules` 
                    (`id`, `sourceUrl`, `moduleKey`, `type`, `title`, `args`, `layoutConfig`, `url`, `isEnabled`, `customSetId`, `sortOrder`, `sourceType`)
                SELECT `id`, `sourceUrl`, `moduleKey`, `type`, 
                    COALESCE(`customTitle`, `title`),
                    `args`, `layoutConfig`, `url`, `isEnabled`,
                    CASE WHEN `customSetId` LIKE 'src_%' OR `customSetId` LIKE 'rss_%' THEN NULL
                         ELSE `customSetId` END,
                    `sortOrder`, 'book'
                FROM `homepage_modules`
                WHERE `isUserCreated` = 1
                AND `sourceUrl` IN (SELECT `bookSourceUrl` FROM `book_sources`)
                """.trimIndent()
            )

            // 5c. 迁移自定义集成员关系
            //     旧表中 customSetId 为 cs_ 前缀的模块，表示用户将书源模块分配到了自定义集
            //     仅迁移书源同步模块（isUserCreated = 0）的分配关系
            //     因为用户创建模块已经在 user_modules 表中有 customSetId 字段
            db.execSQL(
                """
                INSERT OR IGNORE INTO `homepage_custom_set_members` (`customSetId`, `sourceUrl`, `moduleKey`, `sortOrder`)
                SELECT `customSetId`, `sourceUrl`, `moduleKey`, `sortOrder`
                FROM `homepage_modules`
                WHERE `isUserCreated` = 0
                AND `customSetId` LIKE 'cs_%'
                AND `sourceUrl` IN (SELECT `bookSourceUrl` FROM `book_sources`)
                """.trimIndent()
            )

            // 6. 迁移 RSS 源的用户创建模块（sourceUrl 不在 book_sources 表中）
            //    homepage_user_modules 表无外键约束，可直接插入
            db.execSQL(
                """
                INSERT OR IGNORE INTO `homepage_user_modules` 
                    (`id`, `sourceUrl`, `moduleKey`, `type`, `title`, `args`, `layoutConfig`, `url`, `isEnabled`, `customSetId`, `sortOrder`, `sourceType`)
                SELECT `id`, `sourceUrl`, `moduleKey`, `type`, 
                    COALESCE(`customTitle`, `title`),
                    `args`, `layoutConfig`, `url`, `isEnabled`,
                    CASE WHEN `customSetId` LIKE 'src_%' OR `customSetId` LIKE 'rss_%' THEN NULL
                         ELSE `customSetId` END,
                    `sortOrder`, 'rss'
                FROM `homepage_modules`
                WHERE `isUserCreated` = 1
                AND `sourceUrl` NOT IN (SELECT `bookSourceUrl` FROM `book_sources`)
                """.trimIndent()
            )
        }
    }

}
