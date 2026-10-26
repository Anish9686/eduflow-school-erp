import campusImage from '../assets/campus-background.jpg';

/** Shared responsive campus photograph for the authenticated application shell. */
export function CampusBackground({ fullScreen = false }: { fullScreen?: boolean }) {
  return (
    <div className={`campus-background ${fullScreen ? 'campus-background--full' : ''}`} aria-hidden="true">
      <img className="campus-background-image" src={campusImage} alt="" />
    </div>
  );
}
