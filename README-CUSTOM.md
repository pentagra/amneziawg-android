# AmneziaWG Android — custom endpoint-port randomization

This document describes the **custom/endpoint-port-randomization** branch of
[pentagra/amneziawg-android](https://github.com/pentagra/amneziawg-android).
The official upstream is [amnezia-vpn/amneziawg-android](https://github.com/amnezia-vpn/amneziawg-android).

## Изменение относительно upstream

- Перед каждым **новым** запуском userspace-туннеля (`GoBackend` / `awgTurnOn`)
  модифицируется **только временная userspace-конфигурация**.
- Если в конфигурации присутствует **ровно один** корректный `endpoint=` с исходным
  UDP-портом `6437`, порт равновероятно выбирается из 300 значений:
  `6500–6649` (150) и `8300–8449` (150).
- Если подходящего Endpoint нет, либо Endpoint некорректен или неоднозначен,
  конфигурация передаётся без изменений. Сторонние подключения с другими
  портами остаются штатными.
- Во время существования туннеля повторного выбора нет. Восстановление,
  при котором upstream **пересоздаёт** туннель, считается новым запуском.
- Сохранённый VPN-профиль, UI, формат конфигурации и нативный AWG 3.1 не меняются.
- Используется обычный `release` с **оригинальным applicationId**. У кастомной
  сборки свой сертификат подписи: первая установка вместо официального APK
  требует удаления официальной версии и предварительного экспорта профилей.

Точка интеграции: `tunnel/src/main/java/org/amnezia/awg/backend/GoBackend.java`.
Логика: `tunnel/src/main/java/org/amnezia/awg/backend/EndpointPortRandomizer.java`.
Тесты: `tunnel/src/test/java/org/amnezia/awg/backend/EndpointPortRandomizerTest.java`.

**База первой версии upstream:** `ff15093bf3856fea0947923474870b86efb682f0`.
**Метка первой проверенной версии:** `custom-v1-2026-11-02`.

Первая версия успешно собрана и подписана; 9 тестов рандомизатора прошли.
На Android проверены рабочий self-hosted AWG 3.1 (выбор UDP-портов подтверждён
`tcpdump` на VPS) и сторонний VPN без модификации Endpoint.
Эти результаты не являются утверждением об эффективности против DPI.

## Сборка и подпись

Сборочная среда: Ubuntu Server 26.04 LTS, JDK 17, Android SDK Command-Line
Tools, `platforms;android-36`, `build-tools;36.0.0`, NDK
`26.1.10909125`; Gradle запускается штатным `./gradlew`.

Перед сборкой:

```bash
cd ~/projects/amneziawg-android
git switch custom/endpoint-port-randomization
git status --short
git submodule update --init --recursive
```

Ключ находится **вне репозитория**:
`~/.local/share/amneziawg-signing/release.p12` (alias: `amneziawg-custom`).
Нужна надёжная независимая резервная копия файла и пароля. Не публиковать
ключ, VPN-конфигурации, приватные ключи или готовые профили в GitHub.

Для очередной устанавливаемой сборки укажи **новый `versionCode`, больший,
чем у предыдущего установленного кастомного APK**, и отдельный `versionName`:

```bash
./scripts/build-custom-release.sh 13 3.0.1-custom.2
```

Это только пример: выбери код исходя из уже установленного APK и последующих
версий upstream. Параметры передаются Gradle через `-P`, **без изменений
`gradle.properties`**. Скрипт запускает 9 собственных тестов, собирает
release, выполняет 16 KiB alignment, подписывает прежним ключом и проверяет
подпись. Результат сохраняется в `~/amneziawg-apks/`.

В upstream есть два ранее существовавших падающих теста
`BadConfigExceptionTest` (для `SYNTAX_ERROR` и `INVALID_NUMBER`).
Поэтому скрипт запускает конкретно `EndpointPortRandomizerTest`. После
обновлений upstream нужно отдельно отслеживать изменения поведения остальных
тестов — не считать **новые** падения автоматически известной проблемой.

При установке поверх предыдущего кастомного APK должен использоваться **тот
же сертификат подписи**; также необходимо соблюдать правила Android для
обновлений `versionCode`. Перед установкой проверяй отпечаток сертификата.

## Обновление из upstream

Мы используем **merge**, а не rebase, поскольку ветка опубликована: это
сохраняет историю и не требует принудительного push.

```bash
cd ~/projects/amneziawg-android
git switch custom/endpoint-port-randomization
git status --short  # должно быть пусто
git fetch origin --prune --tags
git log --oneline --left-right HEAD...origin/master
# Убедившись, что обновления нужны:
git merge --no-ff origin/master
git submodule update --init --recursive
```

Если возник конфликт, проверить `git status`, вручную разрешить его,
запустить `git add` для исправленных файлов и `git merge --continue`.
Для отмены незавершённого слияния — `git merge --abort`.
**Не использовать `git push --force`** в обычном процессе.

Далее:

```bash
./gradlew --no-daemon :tunnel:testReleaseUnitTest \
  --tests 'org.amnezia.awg.backend.EndpointPortRandomizerTest'
./gradlew --no-daemon :ui:assembleRelease
```

Просмотреть итоговые изменения в точке интеграции и убедиться, что
`EndpointPortRandomizer.rewrite(...)` вызывается перед `awgTurnOn` и профиль
не записывается обратно. На существенных обновлениях выполнить тест
реального UDP-порта на VPS и проверку стороннего профиля.

После **успешных** проверок:

```bash
git push fork custom/endpoint-port-randomization
```

После этого можно собрать подписанный APK скриптом выше, выбрав новый
`versionCode` и `versionName`.

## Git-ремоуты и версия, которую можно восстановить

- `origin`: официальный `https://github.com/amnezia-vpn/amneziawg-android.git`.
- `fork`: персональный `git@github.com:pentagra/amneziawg-android.git`.
- Проверенная первая версия закреплена аннотированным тегом
  `custom-v1-2026-11-02`, опубликованным в `fork`.
- Скрипт и этот файл находятся только в custom-ветке, изменения upstream не
  затрагивают их напрямую (если не появятся файлы с теми же путями).
