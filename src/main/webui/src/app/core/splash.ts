/** Controls the static launch splash (#gg-splash) that lives in index.html - see the comment
 *  there for why it isn't an Angular component. */
const SPLASH_ID = 'gg-splash';

/** Fades the splash out and then removes it - called once the first route has rendered. The
 *  timeout (rather than waiting for `transitionend`) also covers the case where no transition
 *  runs at all, so the splash can never get stuck on top of a working app. */
export function hideSplash(): void {
  const splash = document.getElementById(SPLASH_ID);
  if (!splash) {
    return;
  }
  splash.classList.add('gg-splash--leaving');
  setTimeout(() => splash.remove(), 400);
}

/** Bootstrap failed (typically opening the installed app offline: the /api/auth-config call
 *  in AuthService.init never succeeds) - swap the loading animation for a message and a
 *  retry button instead of leaving an endlessly "loading" screen. */
export function showSplashError(): void {
  document.getElementById(SPLASH_ID)?.classList.add('gg-splash--error');
}
