# VHS tape — records the API demo GIF.
# Prereqs: `brew install vhs`, app running locally (mvn spring-boot:run), and jq.
# Run from the repo root:  vhs docs/demo.tape   ->   docs/demo.gif
Output docs/demo.gif

Set Shell "bash"
Set FontSize 18
Set Width 1200
Set Height 720
Set Theme "Dracula"
Set Padding 20

Type "# 1) Shorten a URL and capture its random short code"
Enter
Type `CODE=$(curl -s -X POST localhost:8080/api/v1/urls -H 'Content-Type: application/json' -d '{"url":"https://spring.io/projects/spring-boot"}' | tee /dev/stderr | jq -r .shortCode)`
Enter
Sleep 2500ms

Type "# 2) Follow the short code -> 302 redirect to the original URL"
Enter
Type `curl -sI localhost:8080/$CODE | grep -i '^location'`
Enter
Sleep 2500ms

Type "# 3) Inspect hit statistics"
Enter
Type `curl -s localhost:8080/api/v1/urls/$CODE | jq`
Enter
Sleep 3s
