package org.example;


import io.restassured.RestAssured;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.notNullValue;

public class AuthIntegrationTest {

    @BeforeAll
    public static void setUp()  {
        RestAssured.baseURI = "http://localhost:4004";
    }

    @Test
    public void shouldReturnOKWithValidToken() {
        //1. Arrange

        String loginPayload = """
                {
                "email": "testuser@test.com",
                "password": "password123"
                }
                """;
        //2. act
        Response response = given()
                .contentType(ContentType.JSON)
                .body(loginPayload)
                .when()
                .post("/auth/login")
                .then()
                .statusCode(200)
                .body("token", notNullValue())
                .extract()
                .response();

        System.out.println("Generated token: " + response.jsonPath().getString("token"));
        //3. assert

    }

    @Test
    public void shouldReturnAuthorizedWithInvalidToken() {
        //1. Arrange

        String loginPayload = """
                {
                "email": "ttestuser@test.com",
                "password": "password123"
                }
                """;
        //2. act
        given()
                .contentType(ContentType.JSON)
                .body(loginPayload)
                .when()
                .post("/auth/login")
                .then()
                .statusCode(401);

    }



}
