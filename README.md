# PROJETO 40 DIAS — app Android

Aplicativo nativo Android (Kotlin + Jetpack Compose + Material 3) para acompanhar 40 dias de redução de gordura com preservação de massa muscular: dieta, treinos, corrida, suplementação, água, medidas corporais, relatórios e lembretes. 100% offline, sem cadastro.

## Como gerar o APK

### Opção A — GitHub Actions (funciona até pelo celular)
1. Crie um repositório (pode ser privado) no GitHub e envie esta pasta inteira.
2. O workflow `.github/workflows/build-apk.yml` roda sozinho a cada envio (ou em **Actions → Gerar APK → Run workflow**).
3. Ao terminar (~5–8 min), abra a execução e baixe o artefato **Projeto40Dias-APK**. Ele contém `app-debug.apk` e `app-release.apk`.
4. No Android, abra o APK e permita "instalar apps desconhecidos".

### Opção B — Android Studio
1. Abra a pasta `Projeto40Dias` no Android Studio (Ladybug ou mais recente).
2. Aguarde a sincronização do Gradle.
3. **Build → Build App Bundle(s) / APK(s) → Build APK(s)**. O arquivo sai em `app/build/outputs/apk/debug/`.

Linha de comando: `./gradlew assembleDebug`

## Testes
- `./gradlew testDebugUnitTest`: cálculos nutricionais, substituições, dia/fase do projeto, relatórios, corrida e regras de recuperação (15 testes).
- `./gradlew connectedDebugAndroidTest` (com aparelho ou emulador): registra refeições, calcula macros, conclui e edita treinos, fecha e reabre o banco e confirma que tudo continua salvo.

## Arquitetura
```
app/src/main/java/br/com/projeto40dias/
├── data/db        Room: entidades, DAOs, banco e dados iniciais (alimentos, cardápio, treinos)
├── data/prefs     DataStore: perfil, metas e lembretes
├── data/repo      Repositórios (camada única de acesso a dados)
├── domain         Regras de negócio puras (nutrição, projeto, relatórios, corrida, recuperação)
├── notifications  WorkManager: lembretes locais
├── pdf            Exportação do relatório em PDF (PdfDocument nativo)
└── ui             Compose: tema, componentes, navegação e telas (MVVM)
```
- **MVVM + Repository**: as telas só conversam com ViewModels, e eles só com os repositórios.
- **Sincronização futura**: todas as entidades têm `updatedAt` e as datas ficam em ISO (`yyyy-MM-dd`). Uma fonte remota pode ser plugada nos repositórios sem mexer na interface.
- **Integração futura com relógios/Health Connect**: os campos de corrida (distância, tempo, FC) já existem. Hoje só o usuário os preenche; o app nunca inventa dados.
- **Gráficos**: desenhados com Canvas do Compose (interativos, sem dependências externas).

## Regras importantes
- Calorias e macros vêm do banco de alimentos (por 100 g) × quantidade. Cada alimento indica se o peso é cru ou cozido.
- Editar um alimento em um dia não altera os dias anteriores. "Salvar como cardápio padrão" aplica a mudança aos dias seguintes.
- Valores aparecem com o selo **MEDIDO**, **ESTIMADO** ou **META**. Resultados de bioimpedância nunca são estimados.
- Metas de calorias abaixo do metabolismo basal são bloqueadas (sem restrição extrema).
- O treino opcional de domingo só aparece quando o check-in indica boa recuperação. Sinais de fadiga geram aviso.
- "Reiniciar projeto" cria um novo ciclo de 40 dias e mantém todo o histórico.
