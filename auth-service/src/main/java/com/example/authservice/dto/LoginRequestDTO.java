package com.example.authservice.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;
import lombok.Getter;
import lombok.Setter;

@Data
@Getter
@Setter
public class LoginRequestDTO {

    @NotBlank(message = "Email is required")
    @Email(message = "Email address is not valid")
    private String email;

    @NotBlank(message = "password is required")
    @Size(min = 4, message = "Password must be at least 4")
    private String password;
}
