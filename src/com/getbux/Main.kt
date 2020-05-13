package com.getbux

import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.json.JSONArray
import org.json.JSONObject
import java.lang.String.format
import java.lang.String.join
import java.net.URI
import java.net.URLEncoder
import java.net.http.HttpClient
import java.nio.charset.StandardCharsets
import java.nio.file.Files
import java.nio.file.Paths

const val token =
    ""

const val newFileHeaders = "Original file line number, buxUserId"



/**
 * TODO:
 * load the .csv file
 * issue requests to usersearch svc
 * for each result, issue request to bos
 * match each result from the request to bos with the row from the file (first name, last name, DOB)
 * write each match in a new file
 */
fun main(args: Array<String>) {
    val client = HttpClient.newHttpClient()

    fun search(firstName: String, lastName: String): List<UserSearchResult> {
        val searchQuery = URLEncoder.encode(join(firstName, lastName, " "), StandardCharsets.UTF_8.toString())
        val response = khttp.get(
            url = URI.create(format("https://bos.prod.getbux.com/cs/api/2/applicants?q=%s", searchQuery)).toString(),
            headers = mapOf(
                "Authorization" to format("Bearer %s", token),
                "Content-type" to "application/json;charset=UTF-8",
                "Accept" to "application/json;charset=UTF-8"
            ))
//        println(request.headers())

        val responseArray:JSONArray = response.jsonArray
//        println(responseArray)
        val typedResponse = mutableListOf<UserSearchResult>()
        for (i in 0 until responseArray.length()) {
            val jsonObj:JSONObject = responseArray.getJSONObject(i)
            //println(jsonObj["buxUserId"])
            typedResponse.add(toUserSearchResult(jsonObj))
        }

        return typedResponse
    }


    fun applicantDetails(id: Long): UserSearchResult {
        val response = khttp.get(
            url = URI.create(format("https://bos.prod.getbux.com/cs/api/2/applicants/%s", id)).toString(),
            headers = mapOf(
                "Authorization" to format("Bearer %s", token),
                "Content-type" to "application/json;charset=UTF-8",
                "Accept" to "application/json;charset=UTF-8"
            ))
        val jsonObj:JSONObject = response.jsonObject
        return toUserSearchResult(jsonObj)
    }

    //print("hello world")
    val sourceFilePath = "/home/gorjan/Downloads/sanctions.csv"
    val destinationFilePath = "/home/gorjan/Downloads/sanctions-with-userids.csv"

    // load the .csv file
    val reader = Files.newBufferedReader(Paths.get(sourceFilePath))
    val writer = Files.newBufferedWriter(Paths.get(destinationFilePath))
    writer.write(newFileHeaders)
    writer.newLine()

    // parse the file into csv values
    val csvParser = CSVParser(reader, CSVFormat.DEFAULT)
    for (csvRecord in csvParser) {
        val lineNumber = csvRecord.recordNumber

        // skip headers
        if (lineNumber == 1L)
            continue

        try {
            val userSearchHits:List<UserSearchResult> = search(csvRecord[2], csvRecord[1])
            if (userSearchHits.isNotEmpty()) {
                for (searchResult in userSearchHits) {
                    val bosResult:UserSearchResult = applicantDetails(searchResult.id);
                    if (searchResult.personalDetails.firstName == bosResult.personalDetails.firstName
                        && searchResult.personalDetails.lastName == bosResult.personalDetails.lastName
                                && searchResult.personalDetails.email == bosResult.personalDetails.email)
                        println(searchResult.buxUserId + bosResult.personalDetails.dateOfBirth)
                }
            }

        } catch (e:Exception) {
            println(format("Exception occurred on line %s", lineNumber))
            e.printStackTrace()
        }

        if (csvRecord.recordNumber > 1L)
            break;
    }
}

fun toUserSearchResult(v:JSONObject) :UserSearchResult {
    val buxUserId:String = v["buxUserId"].toString()
    val id:Long = v["id"].toString().toLong()

    val pd:JSONObject = v.getJSONObject("personalDetails")
    val email:String = when(pd.has("email")) { true -> pd["email"].toString() else -> ""}
    val firstName:String = when(pd.has("firstName")) { true -> pd["firstName"].toString() else -> ""}
    val lastName:String = when(pd.has("lastName")) { true -> pd["lastName"].toString() else -> ""}
    val dateOfBirth:String = when(pd.has("dateOfBirth")) { true -> pd["dateOfBirth"].toString() else -> ""}

    return UserSearchResult(buxUserId, id, PersonalDetails(email, firstName, lastName, dateOfBirth))
}

data class PersonalDetails(
    val email: String,
    val firstName: String,
    val lastName: String,
    val dateOfBirth: String?
)

data class UserSearchResult(
    val buxUserId: String,
    val id: Long,
    val personalDetails: PersonalDetails
)
