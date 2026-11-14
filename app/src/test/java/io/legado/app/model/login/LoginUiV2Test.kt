package io.legado.app.model.login

import io.legado.app.data.entities.rule.RowUi
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 登录界面 v2 协议回归测试：结构校验口径变化会直接影响真实书源（如番茄直连）能否登录。
 */
class LoginUiV2Test {

    @Test
    fun isV2_onlyMatchesVersionMarker() {
        assertTrue(LoginUiV2.isV2(LoginUiV2.MARKER))
        assertTrue(LoginUiV2.isV2(""" {"version": 2} """))
        assertFalse(LoginUiV2.isV2(null))
        assertFalse(LoginUiV2.isV2(""))
        assertFalse(LoginUiV2.isV2("""[{"name":"a","type":"text"}]"""))
        assertFalse(LoginUiV2.isV2("""{"version":1}"""))
    }

    @Test
    fun parseRender_acceptsButtonsAndToggles() {
        // 取自「番茄直连」的 loginUi(state) 返回值
        val json = """
            {"rows":[
              {"name":"刷新设备","type":"button","action":"refreshDevice",
               "style":{"layout_flexGrow":1,"layout_flexBasisPercent":1}},
              {"name":"开启段评","type":"button","action":"enableReview"},
              {"name":"关闭段评","type":"button","action":"disableReview"}
            ]}
        """.trimIndent()
        val rows = LoginUiV2.parseRender(json)
        assertNotNull(rows)
        assertEquals(3, rows!!.size)
        assertEquals("refreshDevice", rows[0].action)
        assertEquals(RowUi.Type.button, rows[0].type)
    }

    @Test
    fun parseRender_keepsKeyAndChoicesForInputs() {
        val json = """
            {"rows":[
              {"name":"手机号","type":"text","key":"phone","hint":"请输入"},
              {"name":"验证码","type":"select","key":"code","options":["1","2"]},
              {"name":"模式","type":"toggle","key":"mode","chars":["✅ ","❎ "],"default":"❎ "}
            ]}
        """.trimIndent()
        val rows = LoginUiV2.parseRender(json)
        assertNotNull(rows)
        assertEquals("phone", rows!![0].key)
        assertEquals(listOf("1", "2"), rows[1].choices())
        assertEquals(listOf("✅ ", "❎ "), rows[2].choices())
    }

    @Test
    fun parseRender_rejectsInvalidRows() {
        // text 行缺 key
        assertNull(LoginUiV2.parseRender("""{"rows":[{"name":"手机号","type":"text"}]}"""))
        // 空 rows
        assertNull(LoginUiV2.parseRender("""{"rows":[]}"""))
        // action 重复（书源按钮无法区分）
        assertNull(
            LoginUiV2.parseRender(
                """{"rows":[{"name":"a","type":"button","action":"x"},{"name":"b","type":"button","action":"x"}]}"""
            )
        )
        // 不是对象
        assertNull(LoginUiV2.parseRender("""[{"name":"a","type":"button","action":"x"}]"""))
        // select 没有候选项
        assertNull(LoginUiV2.parseRender("""{"rows":[{"name":"c","type":"select","key":"k"}]}"""))
    }

    @Test
    fun parseActionResult_readsStateErrorLoginClose() {
        val state = LoginUiV2.parseActionResult("""{"state":{"reviewEnabled":false}}""")
        assertEquals("""{"reviewEnabled":false}""", state.stateJson)
        assertFalse(state.malformed)

        val withError = LoginUiV2.parseActionResult("""{"error":{"phone":"号码不对"}}""")
        assertEquals("号码不对", withError.error?.get("phone"))

        val login = LoginUiV2.parseActionResult("""{"login":{"token":"t"}}""")
        assertEquals("""{"token":"t"}""", login.loginJson)

        val close = LoginUiV2.parseActionResult("""{"close":true}""")
        assertTrue(close.close)
    }

    @Test
    fun parseActionResult_flagsMalformedAndUnknownCommands() {
        assertTrue(LoginUiV2.parseActionResult("""{"state":"oops"}""").malformed)
        assertTrue(LoginUiV2.parseActionResult("""{"close":1}""").malformed)
        assertTrue(LoginUiV2.parseActionResult("""not json""").malformed)

        val unknown = LoginUiV2.parseActionResult(
            """{"state":{},"toast":"hi"}"""
        )
        assertEquals(listOf("toast"), unknown.unknownKeys)
        assertFalse(unknown.malformed)
    }

    @Test
    fun resolveFieldValue_prefersRenderThenSessionThenStored() {
        assertEquals("a", LoginUiV2.resolveFieldValue("a", "b", "c"))
        assertEquals("b", LoginUiV2.resolveFieldValue(null, "b", "c"))
        assertEquals("c", LoginUiV2.resolveFieldValue(null, null, "c"))
        assertNull(LoginUiV2.resolveFieldValue(null, null, null))
    }
}
