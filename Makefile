up:
	docker compose up --build -d

down:
	docker compose down

logs:
	docker compose logs -f --tail=200

backend-test:
	cd backend && mvn test

frontend-test:
	cd frontend && npm test

frontend-build:
	cd frontend && npm run build
