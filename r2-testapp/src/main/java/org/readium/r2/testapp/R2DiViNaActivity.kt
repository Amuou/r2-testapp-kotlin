/*
 * Module: r2-testapp-kotlin
 * Developers: Aferdita Muriqi
 *
 * Copyright (c) 2018. European Digital Reading Lab. All rights reserved.
 * Licensed to the Readium Foundation under one or more contributor license agreements.
 * Use of this source code is governed by a BSD-style license which is detailed in the
 * LICENSE file present in the project repository where this source code is maintained.
 */

package org.readium.r2.testapp

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.view.Menu
import android.view.MenuItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import org.readium.r2.navigator.R2DiViNaActivity
import org.readium.r2.shared.Locator
import timber.log.Timber
import kotlin.coroutines.CoroutineContext


/**
 * R2DiViNaActivity : Extension of the R2DiViNaActivity() from navigator
 *
 * That Activity manage everything related to the menu
 *      ( Table of content, User Settings, Drm, Bookmarks )
 *
 */
class R2DiViNaActivity : R2DiViNaActivity(), CoroutineScope {

    /**
     * Context of this scope.
     */
    override val coroutineContext: CoroutineContext
        get() = Dispatchers.Main

    protected var menuToc: MenuItem? = null


    private var bookId: Long = -1

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Handler().postDelayed({
            bookId = intent.getLongExtra("bookId", -1)
        }, 100)

    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(org.readium.r2.testapp.R.menu.menu_divina, menu)
        menuToc = menu?.findItem(R.id.toc)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {

            R.id.toc -> {
                val intent = Intent(this, R2OutlineActivity::class.java)
                intent.putExtra("publication", publication)
                intent.putExtra("bookId", bookId)
                startActivityForResult(intent, 2)
                return true
            }

            else -> return false
        }

    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        data ?: return
        if (requestCode == 2 && resultCode == Activity.RESULT_OK) {
            val locator = data.getSerializableExtra("locator") as Locator
            Timber.d("locator href ${locator.href}")
        }
    }

}