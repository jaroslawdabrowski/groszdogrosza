import { bootstrapApplication } from '@angular/platform-browser';
import { appConfig } from './app/app.config';
import { App } from './app/app';
import { showSplashError } from './app/core/splash';

bootstrapApplication(App, appConfig)
  .catch((err) => {
    console.error(err);
    showSplashError();
  });
