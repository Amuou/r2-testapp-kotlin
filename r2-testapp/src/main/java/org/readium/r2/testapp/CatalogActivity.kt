package org.readium.r2.testapp

import android.app.Activity
import android.app.ProgressDialog
import android.content.ContentResolver
import android.content.Context
import android.content.DialogInterface
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.support.v7.app.AppCompatActivity
import android.view.LayoutInflater
import android.widget.*
import kotlinx.android.synthetic.main.activity_catalog.*
import org.readium.r2.navigator.R2EpubActivity
import org.readium.r2.navigator.SERVER_URL
import org.readium.r2.streamer.Parser.EpubParser
import org.readium.r2.streamer.Parser.PubBox
import org.readium.r2.streamer.Server.Server
import org.readium.r2.testapp.permissions.PermissionHelper
import org.readium.r2.testapp.permissions.Permissions
import java.io.File
import java.io.IOException
import java.net.URL
import java.util.ArrayList


class CatalogActivity : AppCompatActivity() {

    private val TAG = this::class.java.simpleName

    private val server = Server()

    private lateinit var books:ArrayList<Book>
    private lateinit var booksAdapter: BooksAdapter
    private lateinit var permissionHelper: PermissionHelper
    private lateinit var permissions: Permissions

    private val R2TEST_DIRECTORY_PATH = server.rootDir
    private var EPUB_FILE_NAME:String? = null
    private var PUBLICATION_PATH: String = R2TEST_DIRECTORY_PATH + EPUB_FILE_NAME

    override fun onStart() {
        super.onStart()

        startServer()

        permissionHelper.storagePermission {
            val prefs = getPreferences(Context.MODE_PRIVATE)
            if (!prefs.contains("dummy")) {
                val dir = File(R2TEST_DIRECTORY_PATH)
                if (!dir.exists()) {
                    dir.mkdirs()
                }
                copyEpubFromAssetsToStorage()
                prefs.edit().putBoolean("dummy",true).apply()
            }

            // TODO change to a SQLite DB
            if (books.isEmpty()) {
                val listOfFiles = File(R2TEST_DIRECTORY_PATH).listFilesSafely()
                for (i in listOfFiles.indices) {
                    val file = listOfFiles.get(i)
                    EPUB_FILE_NAME = file.name
                    PUBLICATION_PATH = R2TEST_DIRECTORY_PATH + EPUB_FILE_NAME

                    val pub = EpubParser().parse(PUBLICATION_PATH)
                    if (pub != null) {
                        parseAndShowEpub(pub)

                        val publication = pub.publication
                        var author = ""
                        if (!publication.metadata.authors.isEmpty()) {
                            author = publication.metadata.authors.get(0).name!!
                        }

                        EPUB_FILE_NAME = file.name
                        PUBLICATION_PATH = R2TEST_DIRECTORY_PATH + EPUB_FILE_NAME

                        val baseUrl = URL(SERVER_URL + "/" + file.name)
                        val link = publication.uriTo(publication.coverLink,  baseUrl)
                        val book = Book(file.name, publication.metadata.title, author, file.absolutePath, i.toLong(), link)
                        books.add(book)
                    }
                }
                booksAdapter.notifyDataSetChanged()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        //TODO not sure if this is needed
        stopServer()
    }

    private fun parseIntent() {
        val intent = intent
        val uriString:String? = intent.getStringExtra(R2IntentHelper.URI)
        if (uriString != null) {
            val uri: Uri? = Uri.parse(uriString)
            if (uri != null) {

                val progress = showProgress(this, null, getString(R.string.progress_wait_while_downloading_book))
                progress.show()
                val thread = Thread(Runnable {
                    val FILE_NAME = uri.lastPathSegment
                    val R2TEST_DIRECTORY_PATH = Server().rootDir
                    val PATH = R2TEST_DIRECTORY_PATH + FILE_NAME

                    val input = java.net.URL(uri.toString()).openStream()
                    input.toFile(PATH)

                    val file = File(PATH)

                    try {
                        runOnUiThread(Runnable {

                            val pub = EpubParser().parse(PATH)
                            if (pub != null) {
                                parseAndShowEpub(pub)

                                val publication = pub.publication
                                val container = pub.container

                                server.addEpub(publication, container, "/" + FILE_NAME)

                                val link = publication.uriTo(publication.coverLink, URL(SERVER_URL + "/" + FILE_NAME))
                                val book = Book(file.name, publication.metadata.title, publication.metadata.authors.get(0).name!!, file.absolutePath, books.size.toLong(), link)
                                books.add(book)
                                booksAdapter.notifyDataSetChanged()
                                progress.dismiss()

                            }
                        })
                    } catch (e: Throwable) {
                        e.printStackTrace()
                    }
                })
                thread.start()
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_catalog)

        permissions = Permissions(this)
        permissionHelper = PermissionHelper(this, permissions)

        books = arrayListOf<Book>()
        booksAdapter = BooksAdapter(this, books)
        gridview.adapter = booksAdapter

        gridview.onItemClickListener = AdapterView.OnItemClickListener { _, _, position, _ ->
            val book = books[position]

            EPUB_FILE_NAME = book.fileName
            PUBLICATION_PATH = R2TEST_DIRECTORY_PATH + EPUB_FILE_NAME

            val pub = EpubParser().parse(PUBLICATION_PATH)
            if (pub != null) {
                parseAndShowEpub(pub)

                val publication = pub.publication
                if (publication.spine.size > 0) {
                    val intent = Intent(this, R2EpubActivity::class.java)

                    // TODO might need to change as well, and move into navigator or streamer...
                    intent.putExtra("publication_path", PUBLICATION_PATH)
                    intent.putExtra("epub_name", EPUB_FILE_NAME)
                    intent.putExtra("publication", publication)
                    startActivity(intent)
                }
            }
        }

        gridview.setOnItemLongClickListener { parent, view, position, _ ->

            val layoutInflater = LayoutInflater.from(this)
            val layout = layoutInflater.inflate(org.readium.r2.navigator.R.layout.popup_delete,  parent, false)

            val popup = PopupWindow(this)
            popup.setContentView(layout)
            popup.setWidth(ListPopupWindow.WRAP_CONTENT)
            popup.setHeight(ListPopupWindow.WRAP_CONTENT)
            popup.isOutsideTouchable = true
            popup.isFocusable = true
            popup.showAsDropDown(view, 24, -350)

            val delete: Button = layout.findViewById(R.id.delete) as Button

            delete.setOnClickListener {
                val book = books[position]
                EPUB_FILE_NAME = book.fileName
                PUBLICATION_PATH = R2TEST_DIRECTORY_PATH + EPUB_FILE_NAME
                books.remove(book)
                booksAdapter.notifyDataSetChanged()
                val file = File(PUBLICATION_PATH)
                file.delete()
                popup.dismiss()
            }

            true
        }

        parseIntent();



 
    }

    private fun copyEpubFromAssetsToStorage() {

        val list = assets.list("Samples")

        for (file_name in list) {
            val input = assets.open("Samples/" + file_name)
            EPUB_FILE_NAME = file_name
            PUBLICATION_PATH = R2TEST_DIRECTORY_PATH + "/" + EPUB_FILE_NAME
            input.toFile(PUBLICATION_PATH)

            val file = File(PUBLICATION_PATH)
            val pub = EpubParser().parse(PUBLICATION_PATH)

            if (pub != null) {

                parseAndShowEpub(pub)

                val publication = pub.publication
                val link = publication.uriTo(publication.coverLink,  URL(SERVER_URL + "/" + EPUB_FILE_NAME))

                val book = Book(file.name, publication.metadata.title, publication.metadata.authors.get(0).name!!, file.absolutePath, books.size.toLong(), link)
                books.add(book)
                booksAdapter.notifyDataSetChanged()

            }
        }
    }

    fun startServer() {
        if (!server.isAlive()) {
            try {
                server.start()
            }
            catch (e: IOException)            {
                // do nothing
            }
            server.loadResources(assets)
        }
    }

    fun stopServer() {
        if (server.isAlive()) {
            server.stop()
        }
    }

    private fun parseAndShowEpub(pub: PubBox?) {

        if (pub == null) {
            Toast.makeText(applicationContext, "Invalid ePub", Toast.LENGTH_SHORT).show()
            return
        }
        val publication = pub.publication
        val container = pub.container

        server.addEpub(publication, container, "/" + EPUB_FILE_NAME)

    }

    private fun getContentName(resolver: ContentResolver, uri: Uri): String? {
        val cursor = resolver.query(uri, null, null, null, null)
        cursor!!.moveToFirst()
        val nameIndex = cursor.getColumnIndex(MediaStore.MediaColumns.DISPLAY_NAME)
        if (nameIndex >= 0) {
            val name = cursor.getString(nameIndex)
            cursor.close()
            return name
        } else {
            return null
        }
    }

// TODO needs some rework for deprecated progress indicator...
    private fun showProgress(context: Context, title: String?, message: String?): ProgressDialog {

        val b = ProgressDialog(context)
        b.setButton(DialogInterface.BUTTON_NEGATIVE, getString(R.string.button_dismiss), DialogInterface.OnClickListener { dialogInterface, _ ->
            dialogInterface.dismiss()
        })
        b.setMessage(message)
        b.setTitle(title)

        return b
    }


    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        this.permissions.onRequestPermissionsResult(requestCode, permissions, grantResults)
    }


// TODO needs some rework.
    public override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {

        // The ACTION_OPEN_DOCUMENT intent was sent with the request code
        // READ_REQUEST_CODE. If the request code seen here doesn't match, it's the
        // response to some other intent, and the code below shouldn't run at all.

        if (requestCode == 1 && resultCode == Activity.RESULT_OK) {
            // The document selected by the user won't be returned in the intent.
            // Instead, a URI to that document will be contained in the return intent
            // provided to this method as a parameter.
            // Pull that URI using resultData.getData().
            if (data != null) {
                val uri = data.data
                EPUB_FILE_NAME = getContentName(contentResolver, uri)!!
                PUBLICATION_PATH = R2TEST_DIRECTORY_PATH + EPUB_FILE_NAME
                val input = contentResolver.openInputStream(uri)
                input.toFile(PUBLICATION_PATH)

                val file = File(PUBLICATION_PATH)

                val pub = EpubParser().parse(PUBLICATION_PATH)
                if (pub != null) {
                    parseAndShowEpub(pub)

                    val publication = pub.publication
                    val link = publication.uriTo(publication.coverLink,  URL(SERVER_URL + "/" +EPUB_FILE_NAME))
                    val book = Book(file.name, publication.metadata.title, publication.metadata.authors.get(0).name!!, file.absolutePath, books.size.toLong(), link)
                    books.add(book)
                    booksAdapter.notifyDataSetChanged()

                }


            }
        } else if (requestCode == 2 && resultCode == Activity.RESULT_OK) {

            // existing epub selected through the list activity
            if (data != null) {

                EPUB_FILE_NAME = data.getStringExtra("name")
                PUBLICATION_PATH = R2TEST_DIRECTORY_PATH + EPUB_FILE_NAME
            }

        }
    }
}

