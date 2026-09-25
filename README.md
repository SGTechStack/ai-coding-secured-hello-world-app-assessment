## Running the App

```powershell
# backend/ — http://localhost:8080
cd backend
mvn spring-boot:run "-Dspring-boot.run.profiles=dev" "-Dspring-boot.run.jvmArguments=-Dapp.mail.log-reset-link=true"

# frontend/ — http://localhost:3000
cd frontend
npm run dev
```