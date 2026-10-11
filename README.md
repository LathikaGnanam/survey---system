# Online Feedback / Survey Management System
Lathika Gnanam G - 711525bee035

## Brief Project Description

The  Survey Management System is a web application that lets an administrator create surveys and collect feedback from users. Users open the public page, choose a survey, and submit their answers. The administrator logs in to create or delete surveys and to view the results as bar charts, average ratings, and a list of written comments.

**Features**
- Admin login with a protected dashboard
- Survey creation with rating (1 to 5), multiple-choice, and free-text questions
- Public page to take surveys and submit feedback
- Results view with bar charts, average ratings, and text responses
- Data saved to a file, so surveys and responses remain after a restart

## Technologies 

| Area | Technology |
|------|------------|
| Backend | Java (JDK 17 or newer), built-in `com.sun.net.httpserver` |
| Frontend | HTML5, CSS3, JavaScript |
| Data storage | Java object serialization (`data.ser`) |
| Tools | Visual Studio Code, Git, GitHub |

## Project Structure

```
survey-system/
├── Main.java          (server and REST API)
├── README.md
└── public/
    ├── index.html     (public survey page)
    ├── admin.html     (admin dashboard)
    └── style.css
```

## How to Run

1. Install JDK 17 or newer.
2. Open the `survey-system` folder in VS Code (or a terminal).
3. Compile and start the server:
   ```
   javac Main.java
   java Main
   ```
4. Open http://localhost:8080 in a browser for the public page.
5. Open http://localhost:8080/admin.html for the admin page.

**Admin login:** username `admin`, password `admin123`

## Creating a Survey (Admin)

Enter one question per line:

```
rating|How was the service?
choice|Would you recommend us?|Yes,No,Maybe
text|Any suggestions?
```
