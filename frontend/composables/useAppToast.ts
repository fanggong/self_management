export type AppToastSeverity = 'success' | 'error';

export type AppToastMessage = {
  id: number;
  severity: AppToastSeverity;
  text: string;
};

const APP_TOAST_STATE_KEY = 'app-toast-messages';
const APP_TOAST_DURATION = 4000;

export const useAppToast = () => {
  const toastMessages = useState<AppToastMessage[]>(APP_TOAST_STATE_KEY, () => []);

  const removeToast = (id: number) => {
    toastMessages.value = toastMessages.value.filter((message) => message.id !== id);
  };

  const showToast = (severity: AppToastSeverity, text: string, duration = APP_TOAST_DURATION) => {
    const id = Date.now() + Math.floor(Math.random() * 1000);
    toastMessages.value.push({ id, severity, text });

    if (import.meta.client) {
      window.setTimeout(() => {
        removeToast(id);
      }, duration);
    }
  };

  const clearToasts = () => {
    toastMessages.value = [];
  };

  return {
    toastMessages,
    showToast,
    removeToast,
    clearToasts
  };
};
