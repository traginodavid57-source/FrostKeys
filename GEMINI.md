# Diretrizes do Projeto FrostKeys

## Monitoramento Automático do GitHub Actions CI/CD
Sempre que realizar um `commit` e `push` para o repositório remoto:
1. **Monitoramento em Tempo Real**: Consultar imediatamente a API do GitHub (`https://api.github.com/repos/traginodavid57-source/FrostKeys/actions/runs`) com o token em `~/.git-credentials` para identificar a nova execução disparada.
2. **Acompanhamento Ativo**: Monitorar o progresso dos passos do job (`Setup Gradle`, `compileDebugKotlin`, `compileDebugJavaWithJavac`, `Build Debug APK`, etc.) até a conclusão.
3. **Diagnóstico e Auto-Correção de Falhas**: Se qualquer etapa falhar, baixar e analisar os logs completos do runner (`/actions/jobs/{id}/logs`), diagnosticar o erro exato, aplicar a correção no código, realizar um novo commit/push e continuar monitorando até o build passar com sucesso (`conclusion: success`).
4. **Confirmação e Artefato**: Informar o usuário com o link da execução e o status do artefato APK gerado (`FrostKeys-Debug`).
