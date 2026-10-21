package io.github.mastin95.pliegue.ui.x

import io.github.mastin95.pliegue.AppViewModel
import io.github.mastin95.pliegue.model.XTweet
import io.github.mastin95.pliegue.model.XUser
import io.github.mastin95.pliegue.web.WebPanes
import io.github.mastin95.pliegue.web.XActionKind

/**
 * Me gusta, repost, guardar y seguir desde la interfaz propia: la tarjeta cambia al momento (como en
 * X) y la acción se hace en la web escondida; si X no la acepta, la tarjeta vuelve atrás y se avisa.
 */
class XActor(private val web: WebPanes, private val vm: AppViewModel) {
    private fun can(): Boolean {
        if (web.loggedIn) return true
        vm.showToast("Entra en X para poder hacer esto.")
        return false
    }

    private fun bump(n: Int, on: Boolean) = if (on) n + 1 else (n - 1).coerceAtLeast(0)

    fun like(t: XTweet) {
        toggle(t, !t.liked, XActionKind.Like, XActionKind.Unlike) { p, on -> p.copy(liked = on, likes = bump(p.likes, on)) }
    }

    fun repost(t: XTweet) {
        toggle(t, !t.reposted, XActionKind.Repost, XActionKind.Unrepost) { p, on -> p.copy(reposted = on, reposts = bump(p.reposts, on)) }
    }

    fun bookmark(t: XTweet) {
        val on = !t.bookmarked
        if (toggle(t, on, XActionKind.Bookmark, XActionKind.Unbookmark) { p, o -> p.copy(bookmarked = o, bookmarks = bump(p.bookmarks, o)) }) {
            vm.showToast(if (on) "Añadido a tus Guardados." else "Quitado de tus Guardados.")
        }
    }

    private fun toggle(t: XTweet, on: Boolean, doIt: XActionKind, undo: XActionKind, apply: (XTweet, Boolean) -> XTweet): Boolean {
        if (!can()) return false
        web.store.updateTweet(t.id) { apply(it, on) }
        val kind = if (on) doIt else undo
        web.actions.run(kind, t.id) { ok, why ->
            if (!ok) {
                web.store.updateTweet(t.id) { apply(it, !on) }
                vm.showToast("X no ha aceptado ${kind.label}: $why.")
            }
        }
        return true
    }

    fun follow(u: XUser) {
        if (!can()) return
        val on = !u.followed
        web.store.updateUser(u.handle) { it.copy(followed = on, followers = bump(it.followers, on)) }
        val kind = if (on) XActionKind.Follow else XActionKind.Unfollow
        web.actions.run(kind, u.handle) { ok, why ->
            if (!ok) {
                web.store.updateUser(u.handle) { it.copy(followed = !on, followers = bump(it.followers, !on)) }
                vm.showToast("X no ha aceptado ${kind.label}: $why.")
            }
        }
    }
}
