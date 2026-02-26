# 🎣 FishingBot — ArcheAge

Bot de pesca automática para ArcheAge escrito em **Java 21**. Detecta ícones de skill via visão computacional e envia teclas automaticamente para o jogo, sem necessidade de foco na janela.

---

## 🛠️ Tecnologias

| Tecnologia | Uso |
|---|---|
| **Java 21** | Linguagem principal, Virtual Threads |
| **OpenCV** | Template matching, CLAHE, multi-escala |
| **JNA / JNA Platform** | PostMessage, SendInput, EnumWindows (Win32) |
| **Java AWT Robot** | Captura de tela com correção de DPI |
| **JNativeHook** | Hotkeys globais sem foco na janela |
| **Java Swing** | Interface gráfica com tema escuro |

---

## 📁 Estrutura

```
FishingBot/
├── src/
│   ├── FishingBot.java     # Lógica do bot (loop, detecção, teclas)
│   └── Gui.java            # Interface gráfica e configurações
├── images/                 # Templates PNG das skills
│   ├── up.png
│   ├── left.png
│   ├── right.png
│   ├── pull.png
│   ├── release.png
│   └── target.png
├── lib/                    # opencv_java*.dll e dependências
├── debug/                  # Capturas salvas pelo F1 (gerado em runtime)
└── fishingbot.properties   # Configurações salvas automaticamente
```

---

## ⚙️ Como Funciona

```
Captura tela (região skills + região target)
        ↓
Pré-processamento: BGR → Gray → CLAHE
        ↓
Template matching multi-escala (1.0x, 0.92x, 0.84x)
Score combinado: 65% CCOEFF_NORMED + 35% CCORR_NORMED
        ↓
Buffer de votação (4 frames) — maioria > 50%
        ↓
Confirmação temporal adaptativa
  score ≥ 0.72 → 1 frame
  score ≥ 0.58 → 2 frames
  score <  0.58 → 3 frames
        ↓
Cooldown individual por skill (5.5s)
        ↓
PostMessage → janela do jogo (sem foco)
Fallback: SendInput
```

---

## 🔧 Funções — FishingBot.java

### `loop()`
Loop principal em Virtual Thread. Captura, detecta, vota, confirma e envia a skill. Gerencia cooldowns e detecta quando o peixe é perdido.

### `matchScore(Mat src, Mat tmpl)`
Template matching combinando `TM_CCOEFF_NORMED` (0.65) e `TM_CCORR_NORMED` (0.35) em 3 escalas. Retorna o melhor score combinado.

### `majority(String[] buf)`
Votação com janela de 4 frames. Retorna a skill presente em mais de 50% dos frames recentes — evita falsos positivos por oscilação.

### `framesNeeded(double score)`
Confirmação adaptativa baseada no score. Quanto menor a confiança, mais frames são exigidos antes de enviar.

### `preprocessCapture()` / `preprocessTemplate()`
Pipeline `BGR → Grayscale → CLAHE` aplicado igualmente em templates e capturas, garantindo comparação consistente mesmo com brilho variável.

### `reloadTemplates()`
Carrega e pré-processa todos os PNGs uma vez na memória. Evita I/O no loop principal.

### `captureRegion(Rectangle)`
Captura região da tela com correção automática de DPI scaling via `AffineTransform`.

### `pressKey(int vk)`
Envia tecla via `PostMessage` na janela do jogo (sem foco). Fallback automático para `SendInput`.

### `findGameWindow()`
Enumera janelas via `EnumWindows` e localiza o processo pelo nome do `.exe` usando `QueryFullProcessImageNameW`.

### `saveDebugCapture()`
Salva capturas raw + pré-processadas e loga todos os scores. Acionado pelo **F1**.

---

## 🖥️ Funções — Gui.java

### `selectRegionOnScreen()`
Overlay fullscreen para selecionar regiões arrastando o mouse. Atualiza o bot em tempo real.

### `setupHotkeys()`
Registra hotkeys globais via JNativeHook — funcionam sem foco na janela do bot.

### `loadConfig()` / `saveConfig()`
Persiste regiões, caminhos de imagem, threshold e nome do processo em `fishingbot.properties`.

---

## ⌨️ Mapeamento de Teclas

| Skill | Tecla |
|---|---|
| Seta Cima | `VK_UP` × 2 |
| Seta Esquerda | `VK_LEFT` |
| Seta Direita | `VK_RIGHT` |
| Puxar | `VK_DOWN` × 2 |
| Soltar | `VK_END` |

## 🎮 Hotkeys Globais

| Tecla | Ação |
|---|---|
| `INSERT` | Iniciar bot |
| `END` | Parar bot |
| `F1` | Debug — salvar captura + scores |

---

## 🔍 Configurações

| Parâmetro | Padrão | Descrição |
|---|---|---|
| `confidence` | `0.52` | Threshold mínimo de score para detecção |
| `HIGH_CONF` | `0.72` | Score para confirmação imediata (1 frame) |
| `MED_CONF` | `0.58` | Score para confirmação em 2 frames |
| `SKILL_COOLDOWN_MS` | `5500ms` | Cooldown após envio de uma skill |
| `MAX_APPEARANCE_MS` | `4500ms` | Tempo máximo de uma skill na tela |
| `VOTE_WINDOW` | `4` | Tamanho do buffer de votação |
| `MAX_NO_DETECTION` | `20` | Frames sem detecção antes de resetar estado |

---

## 🐛 Debug

Pressione **F1** durante a execução para salvar em `debug/`:

```
debug/
├── skills_raw_HHMMSS.png     # Captura raw da região de skills
├── skills_proc_HHMMSS.png    # Após CLAHE
├── target_raw_HHMMSS.png     # Captura raw da região do target
└── target_proc_HHMMSS.png    # Após CLAHE
```

O log exibirá os scores de cada template e quantos frames são necessários para confirmar, facilitando o ajuste do threshold e das regiões.

---

## 📋 Requisitos

- Windows (usa Win32 API via JNA)
- Java 21+
- OpenCV 4.x (`opencv_java*.dll` em `lib/`)
- Templates PNG capturados da tela do jogo em `images/`
