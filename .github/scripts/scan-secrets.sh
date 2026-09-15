#!/usr/bin/env bash
# =============================================================================
# Procura credencial em TODO o histórico do repositório.
#
# Olha o histórico inteiro de propósito. Uma chave commitada por engano e
# apagada no commit seguinte continua acessível — e é exatamente essa que
# precisa ser rotacionada no provedor, porque remover o arquivo não a invalida.
#
# O conjunto abaixo cobre o que este projeto realmente usa: Google OAuth,
# Postgres do Supabase, Redis do Render e o segredo de assinatura do JWT.
# =============================================================================
set -uo pipefail

# tipo|regex — formato de credencial, não senha específica, para continuar
# valendo depois de qualquer rotação.
PATTERNS=$(cat <<'EOF'
segredo de cliente Google|GOCSPX-[0-9A-Za-z_-]{20}
token do GitHub|(ghp_|github_pat_)[0-9a-zA-Z_]{20}
chave de acesso AWS|AKIA[0-9A-Z]{16}
URL do Postgres com senha|postgres(ql)?://[^<$"'"'"'[:space:]]+:[^@$"'"'"'[:space:]]+@
JDBC do Postgres com senha|jdbc:postgresql://[^<$"'"'"'[:space:]]+:[^@$"'"'"'[:space:]]+@
URL do Redis com senha|rediss?://[^<$"'"'"'[:space:]]*:[^@$"'"'"'[:space:]]+@
chave privada|BEGIN [A-Z ]*PRIVATE KEY
EOF
)

found=0

while IFS= read -r entry; do
    [ -z "$entry" ] && continue
    kind=${entry%%|*}
    pattern=${entry#*|}

    # `git grep` em todos os commits alcançáveis. O próprio script fica de fora:
    # ele contém os formatos por dever de ofício.
    #
    # Hosts `example.com/org/net` são reservados pela RFC 2606 e nunca apontam
    # para infraestrutura real: é o que os testes usam para exercitar o parsing
    # de uma URL com credencial. Filtrar pelo host, e não pelo caminho do
    # arquivo, mantém um vazamento de verdade detectável dentro de um teste.
    hits=$(git grep -I -n -E -e "$pattern" \
              $(git rev-list --all) -- \
              ':!frontend/.env.example' ':!.github/scripts/scan-secrets.sh' \
           2>/dev/null \
           | grep -v -E 'example\.(com|org|net)' \
           | head -5)

    if [ -n "$hits" ]; then
        echo "::error::$kind encontrada no histórico"
        # Mostra commit e arquivo, nunca a linha inteira — o log da CI é público
        # em repositório público, e imprimir o segredo aqui seria vazá-lo de novo.
        echo "$hits" | cut -d: -f1,2 | sed 's/^/  /'
        found=1
    fi
done <<< "$PATTERNS"

if [ "$found" -ne 0 ]; then
    cat <<'EOF'

Uma credencial está no histórico do repositório. Remover o arquivo agora NÃO
resolve: ela continua acessível em commits anteriores.

1. Rotacione a credencial no provedor (Google Cloud, Supabase, Render).
2. Só então reescreva o histórico, se ainda fizer sentido.
EOF
    exit 1
fi

echo "nenhuma credencial encontrada no histórico"
