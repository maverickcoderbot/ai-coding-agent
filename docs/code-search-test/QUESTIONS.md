# Code-search test: the questions and their known answers

The test behind the video "I Made My AI Code Search Smarter. It Got Worse.". **18 questions, 37 searches** (a question asked in more than one wording counts once per wording). Each question has a known answer: a file and the lines that answer it, read from the source and fixed before the new runs. A search is a **hit** when a chunk containing the answer lines is in the top five results.

Codebases (all Apache-2.0): Spring PetClinic (30 main source files), Gson core (86), Spark (100). Tests are not indexed.


## Spring PetClinic (8 questions, 17 searches)
Q1-Q3 are the three questions from the previous video, searched in the four wordings the models actually wrote (so 12 searches). H1-H5 are five extra questions written to check we had not tuned to Q1-Q3, searched once each.

| id | question | answer | wordings |
|---|---|---|---|
| Q1 | where is the owner telephone number checked to be 10 digits | Owner.java 64 | 4 |
| Q2 | where is the pet required to have a name and a type | PetValidator.java 44-52 | 4 |
| Q3 | where is the check for unique pet names per owner implemented | PetController.java 152-154 | 4 |
| H1 | where are a vet's specialties mapped to the database | Vet.java 47-50 | 1 |
| H2 | where is the visit date format defined | Visit.java 40-41 | 1 |
| H3 | where are owners searched by last name | OwnerRepository.java 45 | 1 |
| H4 | where does the app parse a pet type from text | PetTypeFormatter.java 52 | 1 |
| H5 | where is the home welcome page mapped | WelcomeController.java 25-26 | 1 |

## Gson (5 questions, 10 searches)
Each question asked two ways: a short keyword search and a full sentence.

| id | question | answer | wordings |
|---|---|---|---|
| G1 | where is the annotation that sets a custom JSON field name for serialization defined | SerializedName.java 86-100 | 2 |
| G2 | where does the ISO date parser truncate leap seconds to 59 | ISO8601Utils.java 203-204 | 2 |
| G3 | where is a primitive type converted to its wrapper class | Primitives.java 64-75 | 2 |
| G4 | where can HTML escaping be turned off when building Gson | GsonBuilder.java 614-615 | 2 |
| G5 | where is the default for escaping HTML characters set | GsonBuilder.java 98 | 2 |

## Spark (5 questions, 10 searches)
Each question asked two ways: a short keyword search and a full sentence.

| id | question | answer | wordings |
|---|---|---|---|
| S1 | where does the code check if a string is blank or only whitespace | StringUtils.java 56-67 | 2 |
| S2 | where is a request path matched against a route with path parameters and wildcards | RouteEntry.java 87-88 | 2 |
| S3 | where is a file extension mapped to its content type | MimeType.java 37-99 | 2 |
| S4 | where does the app protect against directory traversal in a requested path | DirectoryTraversal.java 30-33 | 2 |
| S5 | where are the request cookies read into a map | Request.java 476-485 | 2 |

## All wordings


### PetClinic

| label | search text |
|---|---|
| Q1-30b-terse | 10 digits |
| Q1-14b-terse | telephone number 10 digits |
| Q1-8b-sent | where is the owner telephone number checked to be 10 digits |
| Q1-14b-sent | where is the owner telephone number checked to be 10 digits |
| Q2-30b-terse | pet required name type |
| Q2-14b-terse | pet required to have a name and a type |
| Q2-8b-sent | where is the pet required to have a name and a type |
| Q2-14b-sent | where is a pet required to have a name and type checked? |
| Q3-30b-terse | validates uniqueness |
| Q3-14b-terse | duplicate pet name check |
| Q3-8b-sent | where is the check for unique pet names per owner implemented |
| Q3-14b-sent | where is the check for duplicate pet names under the same owner in the app |
| H1-vet-spec | where are a vet's specialties mapped to the database |
| H2-visit-date | where is the visit date format defined |
| H3-owner-lastname | where are owners searched by last name |
| H4-pettype-parse | where does the app parse a pet type from text |
| H5-welcome | where is the home welcome page mapped |

### Gson

| label | search text |
|---|---|
| G1-sent | where is the annotation that sets a custom JSON field name for serialization defined |
| G1-terse | SerializedName annotation alternate names |
| G2-sent | where does the ISO date parser truncate leap seconds to 59 |
| G2-terse | leap second truncate parse date |
| G3-sent | where is a primitive type converted to its wrapper class |
| G3-terse | wrap primitive to wrapper class |
| G4-sent | where can HTML escaping be turned off when building Gson |
| G4-terse | disable html escaping |
| G5-sent | where is the default for escaping HTML characters set |
| G5-terse | default escape html |

### Spark

| label | search text |
|---|---|
| S1-sent | where does the code check if a string is blank or only whitespace |
| S1-terse | is string blank whitespace |
| S2-sent | where is a request path matched against a route with path parameters and wildcards |
| S2-terse | match route path parameters wildcard |
| S3-sent | where is a file extension mapped to its content type |
| S3-terse | file extension mime type mapping |
| S4-sent | where does the app protect against directory traversal in a requested path |
| S4-terse | directory traversal protection path |
| S5-sent | where are the request cookies read into a map |
| S5-terse | request cookies map |
