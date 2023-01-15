package com.example.minerfinder.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.Query

@Dao
interface UserDao {
    @Query("SELECT * FROM user")
    fun getAll(): List<User>

    @Query("SELECT * FROM user WHERE uid IN (:userIds)")
    fun loadAllByIds(userIds: IntArray): List<User>

//    @Query("COUNT * FROM user WHERE username LIKE :user AND " +
//            "password LIKE :pass LIMIT 1")
//    fun checkExistance(user: String, pass: String): Int
    @Query("UPDATE user SET isLogged = :is_in WHERE uid = :userId")
    open fun log_in_out(userId: Int, is_in: Boolean = true)

    @Query("SELECT * FROM user WHERE username LIKE :user AND " +
            "password LIKE :pass LIMIT 1")
    fun findByName(user: String, pass: String): User?

    @Query("SELECT * FROM user WHERE isLogged = 1 LIMIT 1")
    fun findActive(): User?

    @Insert
    fun insertAll(vararg users: User)

    @Delete
    fun delete(user: User)
}