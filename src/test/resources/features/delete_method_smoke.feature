Feature: HTTP Method Override

  NodeAdmin uses a hidden _method field to tunnel DELETE and PUT through HTML
  forms (which only support GET/POST). This feature verifies that SpringAdmin
  honours the HiddenHttpMethodFilter so that web-UI delete actions work, and
  that verbose API paths (NodeAdmin style) return 200 for authenticated users.

  Background:
    Given the application is running

  Scenario: Delete user via form POST with _method=DELETE param
    Given I am logged in as admin
    And a test user exists with code "BDD001" and email "bdd001@example.com"
    When I submit a POST request to "/admin/v1/access/users/{id}" with "_method" = "DELETE"
    Then the response status should be in range 200 to 302
    And the user "bdd001@example.com" should no longer exist

  Scenario: API verbose paths return 200 for authenticated user
    Given I am authenticated via JWT as admin
    When I send a GET request to "/api/v1/access/user"
    Then the response status should be 200
    And the response body should contain a "data" field
