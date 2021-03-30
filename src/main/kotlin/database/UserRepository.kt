package database

import com.github.softwareengineeringclub.database.Database
import com.squareup.sqldelight.Query
import com.squareup.sqldelight.runtime.coroutines.asFlow
import com.squareup.sqldelight.runtime.coroutines.mapToList
import com.toxicbakery.bcrypt.Bcrypt
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import model.User

private const val HASH_SALT_ROUNDS = 6

class UserRepository(private val database: Database) {

    suspend fun getUser(username: String) = withContext(Dispatchers.IO) {
        getUserQuery(username).executeAsOneOrNull()
    }

    fun getUserQuery(username: String): Query<User> {
        return database.userQueries.getUser(username) { username, displayName, _ ->
            User(username, displayName)
        }
    }

    fun getAllUsers() = database.userQueries
        .getAllUsers { username, displayName, _ -> User(username, displayName) }
        .asFlow()
        .mapToList(Dispatchers.IO)

    fun searchUsers(query: String) = database.userQueries
        .searchUser(query, query)
        .asFlow()
        .mapToList(Dispatchers.IO)

    suspend fun removeUser(username: String) = withContext(Dispatchers.IO) {
        database.userQueries.removeUser(username)
    }

    suspend fun insertUser(username: String, displayName: String, unprotectedPassword: String): User {
        val encryptedPassword = encryptPassword(unprotectedPassword)

        withContext(Dispatchers.IO) {
            database.userQueries.insertUser(username, displayName, encryptedPassword)
        }

        val user = getUserQuery(username).executeAsOne()
        return user
    }

    suspend fun isValidLogin(username: String, password: String): User? {
        val localUser = database.userQueries.getUser(username).executeAsOneOrNull() ?: return null

        val isValidPassword =  withContext(Dispatchers.Default) {
            Bcrypt.verify(password, localUser.encryptedPassword.toByteArray(Charsets.UTF_8))
        }

        return if (isValidPassword) {
            User(localUser.username, localUser.displayName)
        } else {
            null
        }
    }

    suspend fun updateDisplayName(username: String, newDisplayName: String) = withContext(Dispatchers.IO) {
        database.userQueries.updateUserDisplayName(newDisplayName, username)
    }

    suspend fun updatePassword(username: String, newPassword: String) = withContext(Dispatchers.IO) {
        database.userQueries.updateUserPassword(encryptPassword(newPassword), username)
    }

    private suspend fun encryptPassword(password: String) = withContext(Dispatchers.Default) {
        Bcrypt.hash(password, HASH_SALT_ROUNDS).toString(Charsets.UTF_8)
    }

}