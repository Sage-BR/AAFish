FishingBot — README
Visão Geral
Bot de pesca automática para ArcheAge escrito em Java 21. Captura a tela, detecta ícones de skill via visão computacional e envia teclas automaticamente para o jogo, sem necessidade de foco na janela.

Tecnologias
Java 21 — linguagem principal, usa Virtual Threads (Thread.ofVirtual()) para o loop do bot e tarefas de debug.
OpenCV (org.opencv) — visão computacional. Usado para template matching, conversão de espaço de cor, CLAHE e redimensionamento multi-escala.
JNA / JNA Platform — acesso nativo ao Windows. Usado para PostMessage, SendInput, EnumWindows e QueryFullProcessImageNameW.
Java AWT Robot — captura de tela via createScreenCapture, com correção de DPI scaling para displays HiDPI.
JNativeHook — hook global de teclado (sem foco) para as hotkeys INSERT, END e F1.
Java Swing — interface gráfica completa com tema escuro customizado.

Estrutura dos Arquivos
FishingBot.java — lógica do bot (loop, detecção, envio de teclas).
Gui.java — interface gráfica, configurações, persistência e hotkeys.
fishingbot.properties — configurações salvas automaticamente (regiões, caminhos, confiança).
images/ — pasta com os templates PNG das skills.
debug/ — capturas salvas pelo F1 para diagnóstico.

Funções Principais — FishingBot.java
loop()
Loop principal em virtual thread. Captura a tela, calcula scores, aplica votação e confirmação temporal, e envia a skill confirmada. Gerencia cooldowns individuais por skill e detecta quando o peixe é perdido.
matchScore(Mat src, Mat tmpl)
Template matching combinando dois métodos: TM_CCOEFF_NORMED (peso 0.65) e TM_CCORR_NORMED (peso 0.35). Testa 3 escalas (1.0, 0.92, 0.84) para cobrir variações de DPI. Retorna o melhor score combinado.
majority(String[] buf)
Buffer de votação com janela de 4 frames. Retorna a skill que aparece em mais de 50% dos frames recentes, reduzindo falsos positivos por oscilação.
framesNeeded(double score)
Confirmação adaptativa: score alto (≥0.72) = 1 frame, médio (≥0.58) = 2 frames, baixo = 3 frames. Evita enviar skills com score instável.
preprocessCapture(Mat) / preprocessTemplate(Mat)
Converte BGR → Grayscale → CLAHE (clipLimit=2.0, tile=8x8). O mesmo pipeline é aplicado em templates e capturas para garantir comparação consistente.
reloadTemplates()
Carrega e pré-processa todos os templates PNG uma vez na memória. Evita I/O no loop principal.
captureRegion(Rectangle)
Captura uma região da tela com correção automática de DPI scaling via AffineTransform.
sendSkill(String) / pressKey(int)
Tenta enviar a tecla via PostMessage na janela do jogo (sem necessidade de foco). Fallback automático para SendInput se a janela não for encontrada.
findGameWindow()
Enumera todas as janelas abertas via EnumWindows e encontra o processo pelo nome do executável usando QueryFullProcessImageNameW.
saveDebugCapture()
Salva captura raw e pré-processada das duas regiões + log de todos os scores e frames necessários. Acionado pela tecla F1.

Funções Principais — Gui.java
build()
Constrói a janela Swing com tema escuro, carrega configurações e registra hotkeys globais.
selectRegionOnScreen(...)
Overlay de tela cheia que permite selecionar uma região arrastando o mouse. Atualiza os spinners e repassa ao bot em tempo real.
setupHotkeys()
Registra INSERT (iniciar), END (parar) e F1 (debug) como hotkeys globais via JNativeHook — funcionam mesmo sem foco na janela do bot.
loadConfig() / saveConfig()
Persiste todas as configurações (regiões, caminhos de imagem, confiança, nome do processo) em fishingbot.properties.
pushConfigToBot()
Sincroniza todos os campos da GUI para o FishingBot antes de iniciar.

Teclas do Jogo
SkillTecla enviadaSeta CimaVK_UP (2x)Seta EsquerdaVK_LEFTSeta DireitaVK_RIGHTPuxarVK_DOWN (2x)SoltarVK_END

Hotkeys Globais
TeclaAçãoINSERTIniciar botENDParar botF1Debug — salvar captura e scores
