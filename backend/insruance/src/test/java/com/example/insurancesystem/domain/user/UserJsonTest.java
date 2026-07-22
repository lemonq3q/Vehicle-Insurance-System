package com.example.insurancesystem.domain.user;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class UserJsonTest {

  private final ObjectMapper objectMapper = new ObjectMapper();

  @Test
  void acceptsPasswordFromLoginRequestButNeverSerializesIt() throws Exception {
    User user =
        objectMapper.readValue(
            "{\"username\":\"15762502276\",\"password\":\"123456\"}", User.class);

    assertEquals("123456", user.getPassword());
    assertFalse(objectMapper.writeValueAsString(user).contains("password"));
  }
}
