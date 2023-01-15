package com.example.minerfinder

import android.os.Bundle
import android.util.Log
import android.widget.EditText
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.room.Room
import com.example.minerfinder.databinding.ActivityAccountBinding
import com.example.minerfinder.db.AppDatabase
import com.example.minerfinder.db.User

class Account : AppCompatActivity() {
    private lateinit var viewBinding: ActivityAccountBinding
    var currentUser : String = ""

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_account)

        viewBinding = ActivityAccountBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)

        viewBinding.addUserButton.setOnClickListener { saveNewUser() }
        viewBinding.listUsersButton.setOnClickListener { displayUsers() }
        viewBinding.loginButton.setOnClickListener { login() }
        viewBinding.logoutButton.setOnClickListener { logout() }

        this.currentUser = this.checkLogged()
        this.displayCurrentUser()
    }

    private fun saveNewUser() {
        val username = findViewById<EditText>(R.id.usernameInput)
        val password = findViewById<EditText>(R.id.passwordInput)
        val user = User(username=username.text.toString(), password=password.text.toString());
        db().userDao().insertAll(user);
//        finish();

        username.text.clear()
        password.text.clear()
    }

    private fun displayCurrentUser() {
        val textView = findViewById<TextView>(R.id.usernameDisplay)

        if (this.currentUser == "") {
            textView.setText("Not Logged In").toString()
        }
        else {
            textView.setText("Logged in as: " + this.currentUser).toString()
        }
    }

    private fun displayUsers() {
        val userList : List<User> = db().userDao().getAll()
        Log.d("DU", userList.toString())
    }

    private fun login() {
        val username = findViewById<EditText>(R.id.usernameInput)
        val password = findViewById<EditText>(R.id.passwordInput)
        var user = db().userDao().findByName(username.text.toString(), password.text.toString())

        if (user != null) {
            Log.d("LOGIN", "FOUND USER")
            db().userDao().log_in_out(user.uid, true)
            this.currentUser = user.username.toString()
            displayCurrentUser()
        }
        else {
            Log.d("LOGIN", "NO USER")
        }

        // clear inputs ui
        username.text.clear()
        password.text.clear()
    }

    private fun logout() {
        val user = db().userDao().findActive()
        if (user != null) {
            db().userDao().log_in_out(user.uid, false)
            this.currentUser = ""
            displayCurrentUser()
        }
    }

    private fun checkLogged(): String {
        var userList = db().userDao().getAll()

        for (user in userList) {
            if (user.isLogged) {
                return user.username.toString()
            }
        }

        return ""
    }

    private fun db(): AppDatabase {
        return Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java, "database-name"
        ).allowMainThreadQueries().fallbackToDestructiveMigration().build()
    }
}