package com.github.hummel.fuploader

import com.google.gson.JsonParser
import org.apache.hc.client5.http.classic.methods.HttpGet
import org.apache.hc.client5.http.classic.methods.HttpPost
import org.apache.hc.client5.http.entity.UrlEncodedFormEntity
import org.apache.hc.client5.http.entity.mime.MultipartEntityBuilder
import org.apache.hc.client5.http.impl.classic.CloseableHttpClient
import org.apache.hc.client5.http.impl.classic.HttpClients
import org.apache.hc.core5.http.ContentType
import org.apache.hc.core5.http.NameValuePair
import org.apache.hc.core5.http.io.entity.EntityUtils
import org.apache.hc.core5.http.message.BasicNameValuePair
import org.apache.hc.core5.net.URIBuilder
import java.io.File

fun main() {
	val config = readConfigFile()
	val address = config.getOrDefault("address", "https://gotminecraftmod.fandom.com/ru/api.php")
	val cooldown = (config.getOrDefault("cooldown", "5").toLong() * 1000L)
	val folderPath = config.getOrDefault("folderPath", "D:/Img")
	val botLogin = config["botLogin"] ?: throw Exception("Wrong login.")
	val botPassword = config["botPassword"] ?: throw Exception("Wrong login.")

	HttpClients.createDefault().use {
		val uriBuilder = URIBuilder(address)

		val loginToken = getLoginToken(uriBuilder, it)
		println("Login token: $loginToken")

		val result = loginViaToken(uriBuilder, it, loginToken, botLogin, botPassword)
		println("Login: $result")

		val csrfToken = getCsrfToken(uriBuilder, it)
		println("Csrf token: $csrfToken")

		val files = File(folderPath).listFiles()
		if (files != null) {
			for (file in files) {
				uploadImage(uriBuilder, it, csrfToken, file)
				Thread.sleep(cooldown)
			}
		}
	}
}

fun uploadImage(
	uriBuilder: URIBuilder, httpClient: CloseableHttpClient, csrfToken: String, file: File
) {
	val url = HttpPost(uriBuilder.build().toString())
	val multipartEntity = MultipartEntityBuilder.create()
		.addTextBody("action", "upload")
		.addTextBody("filename", file.name)
		.addTextBody("format", "json")
		.addTextBody("token", csrfToken)
		.addTextBody("ignorewarnings", "1")
		.addBinaryBody("file", file, ContentType.IMAGE_PNG, file.name)
		.build()

	url.entity = multipartEntity

	httpClient.execute(url) { response ->
		val json = EntityUtils.toString(response.entity)

		uriBuilder.clearParameters()

		println("${file.name}: $json")
	}
}

private fun getLoginToken(
	uriBuilder: URIBuilder, httpClient: CloseableHttpClient
): String {
	uriBuilder.addParameter("action", "query")
	uriBuilder.addParameter("meta", "tokens")
	uriBuilder.addParameter("type", "login")
	uriBuilder.addParameter("format", "json")

	val url = HttpGet(uriBuilder.build().toString())

	return httpClient.execute(url) { response ->
		val json = EntityUtils.toString(response.entity)

		uriBuilder.clearParameters()

		JsonParser.parseString(json).asJsonObject.getAsJsonObject("query")
			.getAsJsonObject("tokens")["logintoken"].asString
	}
}

private fun loginViaToken(
	uriBuilder: URIBuilder, httpClient: CloseableHttpClient, loginToken: String, botLogin: String, botPassword: String
): String {
	val url = HttpPost(uriBuilder.build().toString())
	val params: MutableList<NameValuePair> = mutableListOf(
		BasicNameValuePair("action", "login"),
		BasicNameValuePair("lgname", botLogin),
		BasicNameValuePair("lgpassword", botPassword),
		BasicNameValuePair("format", "json"),
		BasicNameValuePair("lgtoken", loginToken)
	)
	url.entity = UrlEncodedFormEntity(params)

	return httpClient.execute(url) { response ->
		val json = EntityUtils.toString(response.entity)

		uriBuilder.clearParameters()

		JsonParser.parseString(json).asJsonObject.getAsJsonObject("login")["result"].asString
	}
}

private fun getCsrfToken(
	uriBuilder: URIBuilder, httpClient: CloseableHttpClient
): String {
	uriBuilder.addParameter("action", "query")
	uriBuilder.addParameter("meta", "tokens")
	uriBuilder.addParameter("format", "json")

	val url = HttpGet(uriBuilder.build().toString())

	return httpClient.execute(url) { response ->
		val json = EntityUtils.toString(response.entity)

		uriBuilder.clearParameters()

		JsonParser.parseString(json).asJsonObject.getAsJsonObject("query")
			.getAsJsonObject("tokens")["csrftoken"].asString
	}
}

private fun readConfigFile(): Map<String, String> {
	val configFile = File("config.json")
	if (!configFile.exists()) {
		return emptyMap()
	}

	val jsonText = configFile.readText()
	val jsonElement = JsonParser.parseString(jsonText)
	val configMap = mutableMapOf<String, String>()

	jsonElement.asJsonObject.entrySet().forEach { (key, value) ->
		configMap[key] = value.asString
	}

	return configMap
}