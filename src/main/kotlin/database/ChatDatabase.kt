package database

import com.squareup.sqldelight.sqlite.driver.asJdbcDriver
import com.zaxxer.hikari.HikariConfig
import com.github.softwareengineeringclub.database.Database
import com.zaxxer.hikari.HikariDataSource


object ChatDatabase {
    private val config = HikariConfig().apply {
        jdbcUrl  = "jdbc:" + System.getenv("DB_URL")
        username = System.getenv("DB_USERNAME")
        password = System.getenv("DB_PASSWORD")
    }

    private val dataSource = HikariDataSource(config)
    val driver = dataSource.asJdbcDriver()

    private val database = Database(driver)

    val users = UserRepository(database)
    val conversations = ConversationRepository(database, users)
}