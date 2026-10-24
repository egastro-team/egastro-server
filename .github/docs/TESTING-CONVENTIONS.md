## 🧪 Test Generation Guidelines

When generating tests for this project, you must act as an expert Java Software Engineer specializing in high-coverage, resilient code. Adhere strictly to the execution stack, architectural rules, and code patterns defined below.

### 🛠️ Core Testing Stack & Rules
*   **Testing Engine:** Use **JUnit 5** (`org.junit.jupiter.api.*`) exclusively. Do not use JUnit 4 features or annotations.
*   **Mocking Engine:** Use **Mockito** (`org.mockito.*`) for lifecycle isolation and dependency behavior interception.
*   **Assertions Framework:** Use standard **JUnit 5 Assertions** (`org.junit.jupiter.api.Assertions.*`) such as `assertEquals`, `assertTrue`, `assertThrows`, `assertNotNull`, and `assertAll`. *Do not use AssertJ or Hamcrest unless explicitly requested.*

### 📐 Test Layout Pattern (Given-When-Then)
Structure every single test block using descriptive BDD-style layout comments. Keep assertions clear and split multi-sentence structural definitions into separate, punchy logical blocks.

```java
@Test
void should[ExpectedBehavior]_When_[Condition]() {
    // GIVEN
    // Set up test preconditions, initialize inputs, and configure Mockito stubs.

    // WHEN
    // Execute the actual target method under test.

    // THEN
    // Perform JUnit assertions and verify mock interactions.
}
```

### 🎯 Scope & Branch Coverage Targets
*   **100% Branch Coverage:** You must evaluate all execution branches within a target method. Generate individual test cases mapping out the **Happy Path**, every explicit `if/else` condition, edge loops, and runtime validation bounds.
*   **Exception Handling:** Cover every custom error handling block. Validate thrown exceptions explicitly using JUnit's `assertThrows`:
    ```java
    CustomException exception = assertThrows(CustomException.class, () -> {
        service.executeTargetMethod(invalidInput);
    });
    assertEquals("Expected error message", exception.getMessage());
    ```

---

### 📂 Test Type Architecture

#### 1. Isolated Unit Tests (Services / Components)
*   Avoid booting up the heavy Spring Context. Use `@ExtendWith(MockitoExtension.class)` for fast, containerless execution.
*   Inject system components using `@Mock` and `@InjectMocks` annotations.
*   Verify interaction side-effects using Mockito's `verify(mock, times(n)).method()`.

**Example Template:**
```java
@ExtendWith(MockitoExtension.class)
class UserServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private UserServiceImpl userService;

    @Test
    void shouldReturnUser_WhenUserExistsId() {
        // GIVEN
        Long userId = 1L;
        User mockUser = new User(userId, "Alice");
        Mockito.when(userRepository.findById(userId)).thenReturn(Optional.of(mockUser));

        // WHEN
        User result = userService.getUserById(userId);

        // THEN
        assertNotNull(result);
        assertEquals("Alice", result.getName());
        Mockito.verify(userRepository, Mockito.times(1)).findById(userId);
    }
}
```

#### 2. Web Layer Integration Tests (Controllers)
*   Isolate the web layer slice by utilizing the Spring container through `@WebMvcTest(TargetController.class)`.
*   Name integration test classes with the `IT` suffix (for example, `ProductControllerIT`), and include `*IT` in Maven Surefire discovery. Use a separate `@WebMvcTest` slice for each controller, including separate admin controllers.
*   Provide controller mock dependencies directly using Spring's `org.springframework.test.context.bean.override.mockito.MockitoBean` annotation (`@MockitoBean`).
*   Execute tests using an autowired `MockMvc` framework runner.
*   **HTTP Response Validation:** Every integration test must verify both successful and failure responses. Validate the full scope of HTTP status codes, payload structures, and content headers.

**Example Template:**
```java
@WebMvcTest(UserController.class)
class UserControllerOrderIT {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private UserService userService;

    @Test
    void shouldReturnOkStatusAndPayload_WhenUserIsFound() throws Exception {
        // GIVEN
        Long userId = 1L;
        User mockUser = new User(userId, "Alice");
        Mockito.when(userService.getUserById(userId)).thenReturn(mockUser);

        // WHEN & THEN
        mockMvc.perform(MockMvcRequestBuilders.get("/api/users/" + userId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.status().isOk())
                .andExpect(MockMvcResultMatchers.jsonPath("\$.name").value("Alice"));
    }

    @Test
    void shouldReturnNotFoundStatus_WhenUserDoesNotExist() throws Exception {
        // GIVEN
        Long userId = 99L;
        Mockito.when(userService.getUserById(userId))
               .thenThrow(new UserNotFoundException("User not found"));

        // WHEN & THEN
        mockMvc.perform(MockMvcRequestBuilders.get("/api/users/" + userId)
                .contentType(MediaType.APPLICATION_JSON))
                .andExpect(MockMvcResultMatchers.status().isNotFound())
                .andExpect(MockMvcResultMatchers.jsonPath("\$.error").value("User not found"));
    }
}
```

### Shared Test Data

*   Keep entities, request/response DTO factories, security fixtures, JSON payloads, and parameterized inputs in the `com.egastro.testData` test package.
*   Return fresh mutable entities from factory methods to avoid state leaking between tests.
*   Use external `@MethodSource` providers in `testData` for parameterized cases.
*   Keep assertions and MockMvc helpers in `support`; data factories should not perform assertions or call production mappers to build expected DTOs.
