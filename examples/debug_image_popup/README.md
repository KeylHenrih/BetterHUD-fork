# Тестовый попап только из картинок (debug_image_popup)

Этот попап создан для проверки чисто клиентской анимации (`play_once`) на шейдерах в соответствии с Правилом 4.6 (только картинки, без текста, голов и listener).

## Инструкция по установке на сервер

1. Скопируйте файл попапа:
   `cp examples/debug_image_popup/popups/debug_image_popup.yml plugins/BetterHud/popups/debug_image_popup.yml`
2. Скопируйте файл раскладки:
   `cp examples/debug_image_popup/layouts/debug_image_popup.yml plugins/BetterHud/layouts/popups/debug_image_popup.yml`
3. Выполните на сервере:
   `/hud reload`
4. Для тестирования клиентской анимации:
   `/hud debug-popup debug_image_popup hold`
5. Для инспекции состояния анимации и регистрации в реестре:
   `/hud debug-anim`
