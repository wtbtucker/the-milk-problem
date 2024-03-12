package io.milk.start

import freemarker.cache.ClassTemplateLoader
import io.ktor.application.*
import io.ktor.features.*
import io.ktor.freemarker.*
import io.ktor.http.*
import io.ktor.http.content.*
import io.ktor.jackson.*
import io.ktor.request.*
import io.ktor.response.*
import io.ktor.routing.*
import io.ktor.server.engine.*
import io.ktor.server.jetty.*
import io.milk.database.createDatasource
import io.milk.products.ProductDataGateway
import io.milk.products.ProductService
import io.milk.products.PurchaseInfo
import org.slf4j.LoggerFactory
import java.util.*

fun Application.module(jdbcUrl: String, username: String, password: String) {
    val logger = LoggerFactory.getLogger(this.javaClass)
    val dataSource = createDatasource(jdbcUrl, username, password)
    val productService = ProductService(ProductDataGateway(dataSource))

    install(DefaultHeaders)
    install(CallLogging)
    install(FreeMarker) {
        templateLoader = ClassTemplateLoader(this::class.java.classLoader, "templates")
    }
    install(ContentNegotiation) {
        jackson()
    }
    install(Routing) {
        get("/") {
            val products = productService.findAll()
            call.respond(FreeMarkerContent("index.ftl", mapOf("products" to products)))
        }
        post("/api/v1/products") {
            val purchase = call.receive<PurchaseInfo>()

            val currentInventory = productService.findBy(purchase.id)
            logger.info(
                    "current inventory {}, quantity={}, product_id={}",
                    currentInventory.name,
                    currentInventory.quantity,
                    currentInventory.id
            )

            logger.info(
                    "received purchase for {}, quantity={}, product_id={}",
                    purchase.name,
                    purchase.amount,
                    purchase.id
            )
            productService.decrementBy(purchase)
//            productService.update(purchase) // TODO - Replace with decrementBy. Why is using update problematic?

            call.respond(HttpStatusCode.Created)
        }
        static("images") { resources("images") }
        static("style") { resources("style") }
    }
}

fun main() {
    TimeZone.setDefault(TimeZone.getTimeZone("UTC"))
    val port = System.getenv("PORT")?.toInt() ?: 8081
    val jdbcUrl = System.getenv("JDBC_DATABASE_URL")
    val username = System.getenv("JDBC_DATABASE_USERNAME")
    val password = System.getenv("JDBC_DATABASE_USERNAME")

    embeddedServer(Jetty, port, module = { module(jdbcUrl, username, password) }).start()
}