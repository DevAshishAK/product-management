.PHONY: env build test verify up down infra logs k8s-staging
env:        ## generate .env with random secrets + JWT keys
	./scripts/bootstrap-env.sh
build:      ## compile everything, skip tests
	mvn -B -ntp -DskipTests -Djacoco.skip=true package
test:       ## unit tests only
	mvn -B -ntp test
verify:     ## unit + integration tests (Docker required) + coverage gate
	mvn -B -ntp verify
infra:      ## backing services + observability stack
	docker compose --profile infra up -d
up:         ## whole platform
	docker compose --profile apps up -d --build
down:
	docker compose --profile apps down
logs:
	docker compose logs -f --tail=100
k8s-staging:
	kubectl apply -f k8s/namespaces.yaml && kubectl apply -k k8s/overlays/staging
